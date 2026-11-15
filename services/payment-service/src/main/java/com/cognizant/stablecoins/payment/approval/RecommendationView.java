package com.cognizant.stablecoins.payment.approval;
import java.time.Instant;
import java.util.UUID;
/** APPROVED records human review only; this DTO contains no execution capability. */
public record RecommendationView(UUID recommendationId, int version, String paymentId, String correlationId,
        RoutingRecommendation.State state, Instant createdAt, Instant expiresAt,
        long approvalCount, int requiredApprovals, RecommendationDocument document) { }
