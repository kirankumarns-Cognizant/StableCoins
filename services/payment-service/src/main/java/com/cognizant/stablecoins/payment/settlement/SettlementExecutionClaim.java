package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import java.time.Instant;
import java.util.*;

/** Immutable owner claim, not a liquidity reservation or capability to sign/broadcast. */
public record SettlementExecutionClaim(SettlementBinding.Execution execution, String requestedBy,
        String customerPolicyVersion, int requiredApprovals, List<Approval> approvals, Instant claimedAt) {
    public record Approval(String actorId, UUID acceptanceId, Instant acceptedAt, UUID approvalId, Instant approvedAt) {
        public Approval {
            if (actorId == null || actorId.isBlank() || actorId.length() > 255)
                throw new IllegalArgumentException("INVALID_SETTLEMENT_APPROVER");
            Objects.requireNonNull(acceptanceId); Objects.requireNonNull(approvalId);
            Objects.requireNonNull(acceptedAt); Objects.requireNonNull(approvedAt);
            if (approvedAt.isBefore(acceptedAt)) throw new IllegalArgumentException("APPROVAL_BEFORE_ACCEPTANCE");
        }
    }
    public SettlementExecutionClaim {
        Objects.requireNonNull(execution); Objects.requireNonNull(claimedAt);
        if (requestedBy == null || requestedBy.isBlank() || requestedBy.length() > 255)
            throw new IllegalArgumentException("INVALID_EXECUTION_ACTOR");
        FlowContract.identifier(customerPolicyVersion, 50);
        approvals = List.copyOf(approvals);
        var review = execution.review();
        review.requireFresh(claimedAt);
        if (execution.context().token().chain().classification() != SettlementNetwork.Classification.PRIVATE_DEMO
                || !"DEMOUSD".equals(execution.context().token().symbol())
                || requiredApprovals < 2 || approvals.size() < requiredApprovals
                || approvals.stream().map(Approval::actorId).distinct().count() != approvals.size())
            throw new IllegalArgumentException("INDEPENDENT_SETTLEMENT_APPROVALS_REQUIRED");
        for (var approval : approvals) {
            if (approval.acceptedAt().isBefore(review.assessedAt())
                    || approval.approvedAt().isAfter(claimedAt)
                    || !approval.approvedAt().isBefore(review.expiresAt()))
                throw new IllegalArgumentException("SETTLEMENT_APPROVAL_WINDOW_INVALID");
        }
    }
    /** Stable description only; a later worker must revalidate and acquire fresh capacity. */
    public String phase() { return "CLAIMED_NOT_SUBMITTED"; }
}