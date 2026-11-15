package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.approval.RecommendationDocument;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import java.time.Instant;
import java.util.UUID;
/** Read-only, customer-bound source. Implementations must not call lifecycle-mutating reads. */
public interface AgentEvidenceSource {
    record Material(UUID recommendationId,int version,Instant expiresAt,RecommendationDocument document){ }
    Material read(String paymentId,UUID recommendationId,AuthorizedCustomer actor,boolean latest);
}
