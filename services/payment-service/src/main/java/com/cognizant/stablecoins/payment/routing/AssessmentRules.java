package com.cognizant.stablecoins.payment.routing;

import java.math.BigDecimal;
import java.time.Duration;

/** Immutable scoring/gating settings, server-owned and versioned. No inferred confidence scores. */
public record AssessmentRules(String version, Duration maximumEvidenceAge, Duration historicalWindow,
        long minimumSampleSize, int latencyPercentile, BigDecimal minimumSuccessRate,
        BigDecimal maximumFraudScore, BigDecimal maximumPriceDeviationBps,
        int maximumCandidates, int maximumObservations) {
    public AssessmentRules {
        if (version == null || version.isBlank() || maximumEvidenceAge == null || maximumEvidenceAge.isNegative()
                || maximumEvidenceAge.isZero() || maximumEvidenceAge.compareTo(Duration.ofDays(7)) > 0 || historicalWindow == null || historicalWindow.isNegative()
                || historicalWindow.isZero() || historicalWindow.compareTo(Duration.ofDays(365)) > 0 || minimumSampleSize < 1 || latencyPercentile < 1 || latencyPercentile > 100
                || minimumSuccessRate == null || minimumSuccessRate.signum() < 0 || minimumSuccessRate.compareTo(BigDecimal.ONE) > 0
                || maximumFraudScore == null || maximumFraudScore.signum() < 0 || maximumFraudScore.compareTo(new BigDecimal("100")) > 0
                || maximumPriceDeviationBps == null || maximumPriceDeviationBps.signum() < 0
                || maximumCandidates < 1 || maximumCandidates > 100 || maximumObservations < 7 || maximumObservations > 10000) {
            throw new IllegalArgumentException("Invalid assessment rules");
        }
    }
}
