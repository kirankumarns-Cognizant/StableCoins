package com.cognizant.stablecoins.flow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Reservation-aware capacity at one bounded observation, never a reservation or
 * approval. Submitters must acquire/revalidate under the same treasury lock.
 */
public record SettlementAvailability(SettlementPreparation.Evidence preparation,
        BigDecimal tokenCommitted, BigDecimal nativeCommitted, Instant checkedAt) {
    public SettlementAvailability {
        Objects.requireNonNull(preparation, "PREPARATION_REQUIRED");
        var context = preparation.request().instruction().context();
        tokenCommitted = amount(tokenCommitted, context.token().decimals());
        nativeCommitted = amount(nativeCommitted, context.nativeAsset().decimals());
        preparation.requireFor(preparation.request(), checkedAt);
    }
    public BigDecimal tokenAvailable() {
        return preparation.observation().tokenTotal().subtract(tokenCommitted).max(BigDecimal.ZERO);
    }
    public BigDecimal nativeAvailable() {
        return preparation.observation().nativeTotal().subtract(nativeCommitted).max(BigDecimal.ZERO);
    }
    public boolean capacitySufficient() {
        return tokenAvailable().compareTo(preparation.request().instruction().tokenAmount()) >= 0
            && nativeAvailable().compareTo(preparation.observation().executionFeeEstimate()) >= 0;
    }
    public void requireFor(SettlementPreparation.Request expected, Instant now) {
        if (now == null || now.isBefore(checkedAt))
            throw new IllegalStateException("SETTLEMENT_AVAILABILITY_NOT_CURRENT");
        preparation.requireFor(expected, now);
    }
    private static BigDecimal amount(BigDecimal value, int decimals) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > decimals
                || value.precision() - (long)value.scale() > 78)
            throw new IllegalArgumentException("INVALID_COMMITTED_AMOUNT");
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }
}
