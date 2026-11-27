package com.cognizant.stablecoins.flow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Server-produced private-demo quote draft. It grants no approval, reservation or
 * execution authority. USD charges are simulated; native gas is a separate asset.
 * Original RPC evidence is checked at assessment, never refreshed by deserialization.
 */
public record SettlementQuote(SettlementBinding.Review review, SettlementAvailability availability, Terms terms) {
    public record Terms(String version, String providerId, BigDecimal simulatedFeeUsd,
            int validitySeconds, BigDecimal nativeReserveMultiplier) {
        public Terms {
            FlowContract.identifier(version, 50);
            FlowContract.identifier(providerId, 64);
            if (simulatedFeeUsd == null || simulatedFeeUsd.signum() < 0
                    || simulatedFeeUsd.stripTrailingZeros().scale() > 2
                    || simulatedFeeUsd.precision() - (long) simulatedFeeUsd.scale() > 12
                    || validitySeconds < 1 || validitySeconds > 900
                    || nativeReserveMultiplier == null
                    || nativeReserveMultiplier.compareTo(BigDecimal.ONE) < 0
                    || nativeReserveMultiplier.compareTo(BigDecimal.TEN) > 0
                    || nativeReserveMultiplier.stripTrailingZeros().scale() > 4)
                throw new IllegalArgumentException("INVALID_SETTLEMENT_QUOTE_TERMS");
            simulatedFeeUsd = normalized(simulatedFeeUsd);
            nativeReserveMultiplier = normalized(nativeReserveMultiplier);
        }
        public BigDecimal reserve(SettlementObservation observation) {
            return normalized(observation.executionFeeEstimate().multiply(nativeReserveMultiplier)
                    .setScale(observation.instruction().context().nativeAsset().decimals(), RoundingMode.CEILING));
        }
        public Instant expiry(SettlementBinding.Instruction instruction, Instant assessedAt) {
            var maximum = assessedAt.plusSeconds(validitySeconds);
            return maximum.isBefore(instruction.deliverBy()) ? maximum : instruction.deliverBy();
        }
    }

    public SettlementQuote {
        Objects.requireNonNull(review, "REVIEW_REQUIRED");
        Objects.requireNonNull(availability, "AVAILABILITY_REQUIRED");
        Objects.requireNonNull(terms, "QUOTE_TERMS_REQUIRED");
        var request = availability.preparation().request();
        var instruction = review.instruction();
        var context = instruction.context();
        if (instruction.outcome() != SettlementBinding.Outcome.TOKEN_WALLET
                || context.token().chain().classification() != SettlementNetwork.Classification.PRIVATE_DEMO
                || !"DEMOUSD".equals(context.token().symbol())
                || !"BESU_GAS".equals(context.nativeAsset().symbol())
                || context.feeModel() != SettlementNetwork.FeeModel.EXECUTION_ONLY
                || !request.instruction().equals(instruction)
                || !request.requestId().equals(review.preparationId())
                || !terms.providerId().equals(review.providerId())
                || review.feeUsd().compareTo(terms.simulatedFeeUsd()) != 0
                || !review.expiresAt().equals(terms.expiry(instruction, review.assessedAt()))
                || review.nativeFeeReserve().compareTo(terms.reserve(availability.preparation().observation())) != 0)
            throw new IllegalArgumentException("SETTLEMENT_QUOTE_BINDING_CHANGED");
        availability.requireFor(request, review.assessedAt());
        if (availability.tokenAvailable().compareTo(instruction.tokenAmount()) < 0
                || availability.nativeAvailable().compareTo(review.nativeFeeReserve()) < 0)
            throw new IllegalArgumentException("SETTLEMENT_QUOTE_CAPACITY_INSUFFICIENT");
    }

    /** Quote terms may outlive the captured RPC snapshot; execution MUST observe again. */
    public void requireCurrent(Instant now) { review.requireFresh(now); }
    private static BigDecimal normalized(BigDecimal value) {
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }
}
