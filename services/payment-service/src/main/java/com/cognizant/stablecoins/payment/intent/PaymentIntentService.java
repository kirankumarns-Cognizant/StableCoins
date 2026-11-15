package com.cognizant.stablecoins.payment.intent;

import com.cognizant.stablecoins.payment.api.dto.CreatePaymentRequest;
import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.payment.domain.PaymentRail;
import com.cognizant.stablecoins.payment.exception.PaymentAccessException;
import com.cognizant.stablecoins.payment.exception.PaymentValidationException;
import com.cognizant.stablecoins.payment.model.Account;
import com.cognizant.stablecoins.payment.model.Beneficiary;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentIntentService {
    private final Validator validator;
    private final RoutingPolicyProperties properties;
    private final Clock clock;

    @Autowired
    public PaymentIntentService(Validator validator, RoutingPolicyProperties properties) {
        this(validator, properties, Clock.systemUTC());
    }

    PaymentIntentService(Validator validator, RoutingPolicyProperties properties, Clock clock) {
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
    }

    public void requireContext(AuthorizedCustomer actor) {
        if (actor == null) {
            throw new PaymentAccessException("AUTHENTICATION_REQUIRED",
                    "Payment intent requires a verified server-side customer identity");
        }
    }

    public void validate(CreatePaymentRequest request) {
        if (!validator.validate(request).isEmpty()) {
            throw new PaymentValidationException("INVALID_INTENT", "Invalid payment intent or instruction");
        }
    }

    public void authorizeReplay(PaymentIntentSnapshot snapshot, AuthorizedCustomer actor) {
        if (snapshot != null && !snapshot.customerId().equals(actor.customerId())) {
            throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH", "Payment is outside the authorized customer scope");
        }
    }

    public PaymentIntentSnapshot prepare(CreatePaymentRequest request, Account account,
            Beneficiary beneficiary, AuthorizedCustomer actor) {
        requireContext(actor);
        validate(request);
        if (!actor.customerId().equals(account.getCustomerId())
                || !actor.customerId().equals(beneficiary.getCustomerId())) {
            throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH", "Account and beneficiary must belong to the authorized customer");
        }
        if (!request.getCurrency().name().equals(account.getCurrency())) {
            throw invalid("SOURCE_CURRENCY_MISMATCH", "Account currency must match the USD instruction");
        }
        PaymentIntentRequest intent = request.getIntent();
        if (!intent.deliverBy().isAfter(clock.instant())) {
            throw invalid("DEADLINE_EXPIRED", "Delivery deadline must be in the future");
        }
        CustomerRoutingPolicy policy = properties.getPolicies().get(actor.customerId());
        if (policy == null) {
            throw invalid("ROUTING_POLICY_NOT_CONFIGURED", "No server-side routing policy is configured for this customer");
        }
        Set<BeneficiaryOutcome> outcomes = new HashSet<>(intent.allowedAlternatives());
        outcomes.add(intent.requiredOutcome());
        policy.requireAllowed(outcomes,intent.maximumFee());
        if (intent.allowedAlternatives().contains(intent.requiredOutcome())) {
            throw invalid("REDUNDANT_ALTERNATIVE", "An alternative must differ from the required outcome");
        }
        PaymentRail rail = request.getRequestedRail();
        if (rail == PaymentRail.VISA
                || rail == PaymentRail.STABLECOIN && intent.requiredOutcome() != BeneficiaryOutcome.USDC_WALLET
                || rail == PaymentRail.STABLECOIN_PLUS_VISA && intent.requiredOutcome() == BeneficiaryOutcome.USDC_WALLET) {
            throw invalid("RAIL_OUTCOME_MISMATCH", "Requested rail cannot deliver the required beneficiary outcome");
        }
        if (rail != PaymentRail.AUTO && !intent.allowedAlternatives().isEmpty()) {
            throw invalid("ALTERNATIVES_REQUIRE_AUTO", "Alternative outcomes require AUTO routing");
        }
        for (BeneficiaryOutcome outcome : outcomes) {
            if (outcome == BeneficiaryOutcome.USDC_WALLET) {
                if (beneficiary.getWalletAddress() == null
                        || !beneficiary.getWalletAddress().matches("0x[0-9a-fA-F]{40}")) {
                    throw invalid("BENEFICIARY_WALLET_REQUIRED", "Wallet delivery requires a configured EVM wallet address");
                }
            } else if (!outcome.payoutCurrency().equals(beneficiary.getCurrency())) {
                throw invalid("PAYOUT_CURRENCY_MISMATCH", "Beneficiary must support the requested simulated payout currency");
            }
        }
        return new PaymentIntentSnapshot(UUID.randomUUID(), 1, actor.customerId(), actor.actorId(),
                clock.instant(), SettlementAsset.USDC, true,
                outcomes.stream().anyMatch(o -> o != BeneficiaryOutcome.USDC_WALLET), intent, policy);
    }

    private PaymentValidationException invalid(String code, String message) {
        return new PaymentValidationException(code, message);
    }
}
