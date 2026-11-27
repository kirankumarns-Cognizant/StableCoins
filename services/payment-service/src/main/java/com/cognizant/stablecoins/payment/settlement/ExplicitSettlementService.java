package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.api.dto.CreatePaymentRequest;
import com.cognizant.stablecoins.payment.exception.PaymentValidationException;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.Payment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.UUID;

/** Owner intent integration. No approval, signing, accounting or RPC authority. */
@Service
public final class ExplicitSettlementService {
    private final SettlementSelectionProperties properties;
    private final SettlementBindingStore store;
    @Autowired
    public ExplicitSettlementService(SettlementSelectionProperties properties, JdbcTemplate jdbc) {
        this(properties, new SettlementBindingStore(jdbc));
    }
    ExplicitSettlementService(SettlementSelectionProperties properties, SettlementBindingStore store) {
        this.properties = properties;
        this.store = store;
    }

    public ConfirmedSettlement confirm(CreatePaymentRequest request, UUID customer, String recipient) {
        var selection = request.getIntent().settlement();
        if (selection == null) return null;
        if (!properties.getAllowedNetworks().getOrDefault(customer, Set.of()).contains(selection.networkId()))
            throw invalid("SETTLEMENT_NETWORK_NOT_ALLOWED");
        SettlementNetwork.Context context;
        try {
            context = new SettlementNetworkRegistry(properties.getNetworks()).forNewWork(selection.networkId());
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw invalid("SETTLEMENT_NETWORK_UNAVAILABLE");
        }
        // Hard acceptance gate, not an environment flag. Remove only with complete
        // token-aware approval/events/accounting/reconciliation acceptance.
        if (context.token().chain().classification() == SettlementNetwork.Classification.PRIVATE_DEMO)
            throw invalid("BESU_PAYMENT_ACCEPTANCE_PENDING");
        if (!context.token().contract().equals(selection.tokenContract()))
            throw invalid("SETTLEMENT_TOKEN_NOT_ALLOWED");
        var intent = request.getIntent();
        if (intent.requiredOutcome() != BeneficiaryOutcome.USDC_WALLET || !intent.allowedAlternatives().isEmpty()
                || !"USDC".equals(context.token().symbol()) || context.token().decimals() != 6)
            throw invalid("SETTLEMENT_OUTCOME_NOT_SUPPORTED");
        if (request.getAmount().compareTo(selection.tokenAmount()) != 0)
            throw invalid("PUBLIC_USDC_NOTIONAL_AMOUNT_MISMATCH");
        try {
            return new ConfirmedSettlement(context, selection.tokenAmount(), recipient);
        } catch (IllegalArgumentException error) {
            throw invalid("INVALID_SETTLEMENT_INSTRUCTION");
        }
    }

    /** Called after the JPA payment INSERT is flushed, in the SAME owner transaction. */
    public void capture(Payment payment) {
        var instruction = instruction(payment);
        if (instruction != null) store.captureInstruction(instruction);
    }

    public static SettlementBinding.Instruction instruction(Payment payment) {
        var intent = payment.getIntent();
        if (intent == null) return null;
        if (intent.settlement() == null) {
            if (intent.instruction().settlement() != null) throw invalid("SETTLEMENT_INTENT_BINDING_CHANGED");
            return null;
        }
        var confirmed = intent.settlement();
        var selected = intent.instruction().settlement();
        if (selected == null || !selected.networkId().equals(confirmed.context().token().chain().networkId())
                || !selected.tokenContract().equals(confirmed.context().token().contract())
                || selected.tokenAmount().compareTo(confirmed.tokenAmount()) != 0
                || (intent.instruction().requiredOutcome() == BeneficiaryOutcome.USDC_WALLET
                    && payment.getAmount().compareTo(confirmed.tokenAmount()) != 0)
                || (intent.instruction().requiredOutcome() != BeneficiaryOutcome.USDC_WALLET
                    && intent.instruction().requiredOutcome() != BeneficiaryOutcome.TOKEN_WALLET)
                || !intent.instruction().allowedAlternatives().isEmpty())
            throw invalid("SETTLEMENT_INTENT_BINDING_CHANGED");
        return new SettlementBinding.Instruction(intent.intentId(), intent.intentVersion(), intent.customerId(),
                payment.getPaymentId(), payment.getCorrelationId(), confirmed.context(), confirmed.recipient(),
                payment.getAmount(), confirmed.tokenAmount(),
                SettlementBinding.Outcome.valueOf(intent.instruction().requiredOutcome().name()),
                intent.instruction().maximumFee(), intent.instruction().deliverBy());
    }

    public static boolean isExplicit(Payment payment) {
        return payment.getIntent() != null && (payment.getIntent().settlement() != null
                || payment.getIntent().instruction().settlement() != null);
    }

    /** Never reinterpret a bound instruction as the old default-network USDC flow. */
    public static void requireLegacyWorkflow(Payment payment) {
        if (isExplicit(payment)) throw invalid("SETTLEMENT_WORKFLOW_ACCEPTANCE_PENDING");
    }
    private static PaymentValidationException invalid(String code) {
        return new PaymentValidationException(code, "Explicit settlement is unavailable for this instruction");
    }
}
