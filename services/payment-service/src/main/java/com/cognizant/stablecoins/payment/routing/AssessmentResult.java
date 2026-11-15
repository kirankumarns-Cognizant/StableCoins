package com.cognizant.stablecoins.payment.routing;

import com.cognizant.stablecoins.payment.intent.BeneficiaryOutcome;
import com.cognizant.stablecoins.payment.intent.RoutingPriority;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.cognizant.stablecoins.payment.routing.RoutingEvidence.*;

/** Deterministic assessment only; preferredCandidateId is not approval, reservation or rail selection. */
public record AssessmentResult(String paymentId, String correlationId, UUID intentId, int intentVersion,
        String policyVersion, String ruleVersion, UUID evidenceSnapshotId, Instant assessedAt,
        Instant expiresAt, RoutingPriority priority, Status status, String preferredCandidateId,
        Map<BeneficiaryOutcome, List<String>> rankedCandidateIds, List<CandidateAssessment> candidates) {
    public AssessmentResult {
        var copy = new EnumMap<BeneficiaryOutcome, List<String>>(BeneficiaryOutcome.class);
        rankedCandidateIds.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        rankedCandidateIds = Collections.unmodifiableMap(copy);
        candidates = List.copyOf(candidates);
    }
    public enum Status { ASSESSED, NO_ELIGIBLE_ROUTE, INSUFFICIENT_EVIDENCE, OUTCOME_CHOICE_REQUIRED }
    public enum Eligibility { ELIGIBLE, INELIGIBLE, INSUFFICIENT_EVIDENCE }
    public enum ReasonCode {
        UNSUPPORTED_PATH, OUTCOME_NOT_ALLOWED, PROVIDER_NOT_ALLOWED, REQUESTED_RAIL_MISMATCH,
        UNSUPPORTED_NETWORK, MISSING_SIGNAL, DUPLICATE_SIGNAL, UNSUPPORTED_SCHEMA, INVALID_SIGNAL,
        UNKNOWN_SIGNAL, CONTRADICTORY_SIGNAL, SCOPE_MISMATCH, NETWORK_MISMATCH, UNIT_MISMATCH,
        SIGNAL_TYPE_MISMATCH, FUTURE_EVIDENCE, STALE_EVIDENCE, INCOMPATIBLE_HISTORY,
        INSUFFICIENT_HISTORY, SUCCESS_RATE_BELOW_MINIMUM, CHAIN_ONLY_ESTIMATE, DEADLINE_EXCEEDED,
        COST_COMPONENTS_INCOMPLETE, COST_TOTAL_MISMATCH, INVALID_CONVERSION, UNSUPPORTED_COST_BASIS,
        FEE_LIMIT_EXCEEDED, FRAUD_LIMIT_EXCEEDED, SIMULATED_SCREENING_REQUIRED, SIMULATED_PAYOUT_EVIDENCE_REQUIRED, RAIL_UNAVAILABLE,
        RAIL_DEGRADED, INSUFFICIENT_USDC, INSUFFICIENT_ETH, INSUFFICIENT_FIAT,
        MISSING_PAYOUT_REQUIREMENT, EXTERNAL_BLOCKER, PRICE_DEVIATION_EXCEEDED
    }
    public record Finding(ReasonCode code, SignalType signalType, String signalId) { }
    public record EvidenceReference(String signalId, SignalType signalType, String sourceId,
            String sourceRecordReference, EvidenceMode evidenceMode, Quality quality,
            Instant observedAt, Instant receivedAt, Instant expiresAt) { }
    public record CandidateAssessment(Candidate candidate, Eligibility eligibility, List<Finding> findings,
            BigDecimal totalCostUsd, List<CostItem> itemizedCosts, Long deliveryMillis,
            BigDecimal successRate, BigDecimal fraudScore, Instant validUntil, Set<EvidenceMode> evidenceModes,
            List<EvidenceReference> evidence) {
        public CandidateAssessment {
            findings = List.copyOf(findings); itemizedCosts = List.copyOf(itemizedCosts);
            evidenceModes = Set.copyOf(evidenceModes); evidence = List.copyOf(evidence);
        }
    }
}
