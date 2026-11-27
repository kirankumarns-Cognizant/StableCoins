package com.cognizant.stablecoins.flow;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Instruction-bound, bounded-window RPC evidence for an execution-only fee model.
 * Totals are NOT spendable balances: reservations have not been checked or acquired.
 * This value grants no quote, approval, reservation or execution authority.
 * It must not be used for Base's additional L1 fee model.
 */
public record SettlementObservation(SettlementBinding.Instruction instruction, long observedBlock,
        BigDecimal tokenTotal, BigDecimal nativeTotal, BigDecimal estimatedGas, BigDecimal gasLimit,
        BigDecimal gasPriceWei, BigDecimal executionFeeEstimate, Instant observedAt, Instant expiresAt) {
    public SettlementObservation {
        Objects.requireNonNull(instruction, "INSTRUCTION_REQUIRED");
        if (instruction.context().feeModel() != SettlementNetwork.FeeModel.EXECUTION_ONLY)
            throw new IllegalArgumentException("OBSERVATION_REQUIRES_EXECUTION_ONLY_FEE_MODEL");
        if (observedBlock <= 0) throw new IllegalArgumentException("INVALID_OBSERVED_BLOCK");
        tokenTotal = amount(tokenTotal, instruction.context().token().decimals());
        nativeTotal = amount(nativeTotal, instruction.context().nativeAsset().decimals());
        executionFeeEstimate = amount(executionFeeEstimate, instruction.context().nativeAsset().decimals());
        estimatedGas = integer(estimatedGas);
        gasLimit = integer(gasLimit);
        gasPriceWei = integer(gasPriceWei);
        if (estimatedGas.signum() <= 0 || gasLimit.compareTo(estimatedGas) < 0
                || gasLimit.compareTo(new BigDecimal("30000000")) > 0
                || gasPriceWei.toBigIntegerExact().bitLength() > 128
                || executionFeeEstimate.compareTo(gasLimit.multiply(gasPriceWei).movePointLeft(18)) != 0)
            throw new IllegalArgumentException("INVALID_OBSERVED_FEE_ESTIMATE");
        if (observedAt == null || expiresAt == null || !expiresAt.isAfter(observedAt)
                || Duration.between(observedAt, expiresAt).compareTo(Duration.ofSeconds(15)) > 0
                || expiresAt.isAfter(instruction.deliverBy()))
            throw new IllegalArgumentException("INVALID_OBSERVATION_WINDOW");
    }

    /** Full owner material must match; matching only a network/symbol/amount is insufficient. */
    public void requireFor(SettlementBinding.Instruction expected, Instant now) {
        if (!instruction.equals(Objects.requireNonNull(expected, "INSTRUCTION_REQUIRED")))
            throw new IllegalStateException("SETTLEMENT_OBSERVATION_BINDING_CHANGED");
        Objects.requireNonNull(now, "OBSERVATION_TIME_REQUIRED");
        if (now.isBefore(observedAt) || !now.isBefore(expiresAt))
            throw new IllegalStateException("SETTLEMENT_OBSERVATION_NOT_CURRENT");
    }

    /** Capacity against raw totals only; never an executable/readiness decision. */
    public boolean totalsCoverEstimate() {
        return tokenTotal.compareTo(instruction.tokenAmount()) >= 0
                && nativeTotal.compareTo(executionFeeEstimate) >= 0;
    }

    private static BigDecimal amount(BigDecimal value, int decimals) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > decimals
                || value.precision() - (long) value.scale() > 78)
            throw new IllegalArgumentException("INVALID_OBSERVED_BALANCE");
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    private static BigDecimal integer(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 0
                || value.precision() - (long) value.scale() > 78)
            throw new IllegalArgumentException("INVALID_OBSERVED_GAS");
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }
}
