package com.cognizant.stablecoins.payment.approval;
import com.cognizant.stablecoins.payment.routing.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** Immutable server-produced material. No HTTP endpoint accepts evidence or quotes from a caller. */
public record RecommendationDocument(com.cognizant.stablecoins.payment.intent.PaymentIntentSnapshot intent, AssessmentContext context, RoutingEvidence.Snapshot evidence,
        AssessmentResult assessment, Quote quote, InstructionBinding instruction, String rationale, String producerVersion) {
    public record InstructionBinding(UUID senderAccountId, UUID beneficiaryId, String recipientWallet, String payoutCurrency) { }
    public record Quote(UUID quoteId, int version, String paymentId, String correlationId,
            UUID customerId, String candidateId, String providerId, BigDecimal sourceAmountUsd,
            BigDecimal transferAmountUsdc, Map<com.cognizant.stablecoins.payment.intent.BeneficiaryOutcome, BigDecimal> payoutAmounts,
            BigDecimal totalFeeUsd, Instant createdAt, Instant expiresAt, boolean simulated) {
        public Quote { payoutAmounts = Map.copyOf(payoutAmounts); }
    }
}
