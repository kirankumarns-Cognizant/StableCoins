package com.cognizant.stablecoins.payment.approval;
import com.cognizant.stablecoins.payment.routing.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** Immutable server-produced material. No HTTP endpoint accepts evidence or quotes from a caller. */
public record RecommendationDocument(com.cognizant.stablecoins.payment.intent.PaymentIntentSnapshot intent, AssessmentContext context, RoutingEvidence.Snapshot evidence,
        AssessmentResult assessment, Quote quote, InstructionBinding instruction, String rationale, String producerVersion,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        com.cognizant.stablecoins.flow.SettlementQuote settlementQuote) {
    public RecommendationDocument {
        if (settlementQuote != null && (intent == null || instruction == null || context != null
                || evidence != null || assessment != null || quote != null))
            throw new IllegalArgumentException("MIXED_SETTLEMENT_REVIEW_MATERIAL");
    }
    public RecommendationDocument(com.cognizant.stablecoins.payment.intent.PaymentIntentSnapshot intent,
            AssessmentContext context, RoutingEvidence.Snapshot evidence, AssessmentResult assessment,
            Quote quote, InstructionBinding instruction, String rationale, String producerVersion) {
        this(intent, context, evidence, assessment, quote, instruction, rationale, producerVersion, null);
    }
    /** Canonical recommendation version; the original immutable draft remains version one. */
    public com.cognizant.stablecoins.flow.SettlementBinding.Review settlementReview(int version) {
        var q = Objects.requireNonNull(settlementQuote, "SETTLEMENT_QUOTE_REQUIRED").review();
        return new com.cognizant.stablecoins.flow.SettlementBinding.Review(q.recommendationId(), version,
                q.preparationId(), q.quoteId(), q.quoteVersion(), q.providerId(), q.instruction(),
                q.feeUsd(), q.nativeFeeReserve(), q.assessedAt(), q.expiresAt());
    }
    public record InstructionBinding(UUID senderAccountId, UUID beneficiaryId, String recipientWallet, String payoutCurrency) { }
    public record Quote(UUID quoteId, int version, String paymentId, String correlationId,
            UUID customerId, String candidateId, String providerId, BigDecimal sourceAmountUsd,
            BigDecimal transferAmountUsdc, Map<com.cognizant.stablecoins.payment.intent.BeneficiaryOutcome, BigDecimal> payoutAmounts,
            BigDecimal totalFeeUsd, Instant createdAt, Instant expiresAt, boolean simulated) {
        public Quote { payoutAmounts = Map.copyOf(payoutAmounts); }
    }
}
