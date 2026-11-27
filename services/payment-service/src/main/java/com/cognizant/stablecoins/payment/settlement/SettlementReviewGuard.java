package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.approval.ApprovalConflictException;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** Server-owned draft lookup and policy validation; never grants execution authority. */
@Component
@ConditionalOnProperty(name="app.flow.enabled", havingValue="true")
public class SettlementReviewGuard {
    private final SettlementQuoteService quotes;
    private final SettlementSelectionProperties selections;
    private final SettlementExecutionReads claims;
    @org.springframework.beans.factory.annotation.Autowired
    public SettlementReviewGuard(SettlementQuoteService quotes, SettlementSelectionProperties selections,
            org.springframework.jdbc.core.JdbcTemplate jdbc) { this(quotes,selections,new SettlementExecutionReads(jdbc)); }
    public SettlementReviewGuard(SettlementQuoteService quotes, SettlementSelectionProperties selections) { this(quotes,selections,(SettlementExecutionReads)null); }
    public SettlementReviewGuard(SettlementQuoteService quotes, SettlementSelectionProperties selections,SettlementExecutionReads claims) {
        this.quotes=quotes;this.selections=selections;this.claims=claims;
    }
    public SettlementQuote load(String paymentId, UUID preparationId, UUID id, AuthorizedCustomer actor) {
        return quotes.get(paymentId, preparationId, id, actor);
    }
    public void validate(Payment payment, SettlementQuote quote, Instant now) {
        validateTerms(payment,quote,now);
        if(quotes.executionClaimed(payment.getPaymentId())) throw conflict("SETTLEMENT_EXECUTION_ALREADY_CLAIMED");
    }
    /** Existing claim recovery does not permit a new review or a different execution. */
    public void validateClaimed(Payment payment, SettlementQuote quote, SettlementBinding.Execution execution, Instant now) {
        if(claims==null) throw conflict("SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
        claims.requireClaimed(execution);
        var q=quote.review();
        var approved=new SettlementBinding.Review(q.recommendationId(),execution.review().version(),q.preparationId(),
                q.quoteId(),q.quoteVersion(),q.providerId(),q.instruction(),q.feeUsd(),q.nativeFeeReserve(),q.assessedAt(),q.expiresAt());
        execution.requireSameReview(approved);
        if(!quotes.executionClaimed(payment.getPaymentId())) throw conflict("SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
        validateTerms(payment,quote,now);
    }
    private void validateTerms(Payment payment, SettlementQuote quote, Instant now) {
        var intent=payment.getIntent();
        var binding=ExplicitSettlementService.instruction(payment);
        if (binding==null || !binding.equals(quote.review().instruction())
                || intent.settlementAsset()!=SettlementAsset.DEMOUSD || !intent.fundingSimulated()
                || intent.fiatPayoutSimulated() || !intent.instruction().confirmed()
                || intent.instruction().requiredOutcome()!=BeneficiaryOutcome.TOKEN_WALLET
                || !"USD".equals(payment.getSourceCurrency()) || !"STABLECOIN".equals(payment.getPaymentRail()))
            throw conflict("SETTLEMENT_REVIEW_INSTRUCTION_CHANGED");
        var policy=intent.policy();
        policy.requireAllowed(Set.of(BeneficiaryOutcome.TOKEN_WALLET),binding.maximumFeeUsd());
        if(policy.requiredApprovals()<2) throw conflict("TWO_INDEPENDENT_APPROVERS_REQUIRED");
        if(!policy.allowedProviderIds().contains(quote.review().providerId()))
            throw conflict("SETTLEMENT_PROVIDER_NOT_ALLOWED");
        String network=binding.context().token().chain().networkId();
        if(!selections.getAllowedNetworks().getOrDefault(intent.customerId(),Set.of()).contains(network))
            throw conflict("SETTLEMENT_NETWORK_NOT_ALLOWED");
        SettlementNetwork.Context configured;
        try { configured=new SettlementNetworkRegistry(selections.getNetworks()).forNewWork(network); }
        catch(IllegalArgumentException | IllegalStateException e) { throw conflict("SETTLEMENT_NETWORK_UNAVAILABLE"); }
        if(!configured.equals(binding.context())) throw conflict("SETTLEMENT_CONTEXT_CHANGED");
        try { quote.requireCurrent(now); }
        catch(IllegalStateException e) { throw conflict("SETTLEMENT_QUOTE_EXPIRED"); }
        // Original RPC evidence is historical. Execution must acquire a fresh observation/hold.
    }
    private static ApprovalConflictException conflict(String code) { return new ApprovalConflictException(code); }
}
