package com.cognizant.stablecoins.flow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-016 material for the additive multi-network workflow.
 * These values carry terms; constructing or storing them grants no approval or signing authority.
 * Legacy USDC FlowContract documents remain unchanged.
 */
public final class SettlementBinding {
    private SettlementBinding() {}
    public enum Outcome { USDC_WALLET, TOKEN_WALLET }

    public record Instruction(UUID intentId, int intentVersion, UUID customerId,
            String paymentId, String correlationId, SettlementNetwork.Context context,
            String recipient, BigDecimal sourceUsd, BigDecimal tokenAmount,
            Outcome outcome, BigDecimal maximumFeeUsd, Instant deliverBy) {
        public Instruction {
            Objects.requireNonNull(intentId, "INTENT_REQUIRED");
            Objects.requireNonNull(customerId, "CUSTOMER_REQUIRED");
            if (intentVersion < 1) throw new IllegalArgumentException("INVALID_INTENT_VERSION");
            FlowContract.identifier(paymentId, 50);
            FlowContract.identifier(correlationId, 60);
            Objects.requireNonNull(context, "SETTLEMENT_CONTEXT_REQUIRED");
            recipient = SettlementNetwork.address(recipient);
            if (recipient.equals(context.treasury()))
                throw new IllegalArgumentException("SELF_SETTLEMENT_NOT_SUPPORTED");
            sourceUsd = FlowContract.money(sourceUsd, 2);
            tokenAmount = FlowContract.money(tokenAmount, context.token().decimals());
            maximumFeeUsd = nonnegative(maximumFeeUsd, 2, "INVALID_MAXIMUM_FEE");
            Objects.requireNonNull(deliverBy, "DEADLINE_REQUIRED");
            Objects.requireNonNull(outcome, "OUTCOME_REQUIRED");
            if (outcome == Outcome.USDC_WALLET &&
                    (!"USDC".equals(context.token().symbol()) ||
                     context.token().chain().classification() != SettlementNetwork.Classification.PUBLIC_TESTNET))
                throw new IllegalArgumentException("USDC_OUTCOME_REQUIRES_PUBLIC_USDC");
            // Source USD and token units are distinct quantities, never an inferred exchange rate.
        }
    }

    public record Review(UUID recommendationId, int version, UUID preparationId,
            UUID quoteId, int quoteVersion, String providerId, Instruction instruction,
            BigDecimal feeUsd, BigDecimal nativeFeeReserve, Instant assessedAt, Instant expiresAt) {
        public Review {
            Objects.requireNonNull(recommendationId, "RECOMMENDATION_REQUIRED");
            Objects.requireNonNull(preparationId, "PREPARATION_REQUIRED");
            Objects.requireNonNull(quoteId, "QUOTE_REQUIRED");
            Objects.requireNonNull(instruction, "INSTRUCTION_REQUIRED");
            if (version < 1 || quoteVersion < 1) throw new IllegalArgumentException("INVALID_REVIEW_VERSION");
            FlowContract.identifier(providerId, 64);
            feeUsd = nonnegative(feeUsd, 2, "INVALID_QUOTED_FEE");
            nativeFeeReserve = nonnegative(nativeFeeReserve, instruction.context().nativeAsset().decimals(),
                    "INVALID_NATIVE_FEE_RESERVE");
            if (feeUsd.compareTo(instruction.maximumFeeUsd()) > 0)
                throw new IllegalArgumentException("CUSTOMER_FEE_LIMIT_EXCEEDED");
            if (assessedAt == null || expiresAt == null || !expiresAt.isAfter(assessedAt) ||
                    expiresAt.isAfter(instruction.deliverBy()))
                throw new IllegalArgumentException("INVALID_REVIEW_WINDOW");
        }

        /** Includes the chain, genesis and asset; provider names cannot collide across networks. */
        public String candidateId() {
            return instruction.context().token().key() + ":" + providerId;
        }

        public void requireFresh(Instant now) {
            Objects.requireNonNull(now);
            if (now.isBefore(assessedAt) || !now.isBefore(expiresAt))
                throw new IllegalStateException("SETTLEMENT_REVIEW_NOT_CURRENT");
        }
    }

    public record Execution(UUID executionId, Review review) {
        public Execution {
            Objects.requireNonNull(executionId, "EXECUTION_REQUIRED");
            Objects.requireNonNull(review, "REVIEW_REQUIRED");
        }
        public Instruction instruction() { return review.instruction(); }
        public SettlementNetwork.Context context() { return instruction().context(); }
        /** Exact material equality, not just chain ID, symbol, quote ID or provider. */
        public void requireSameReview(Review approved) {
            if (!review.equals(approved)) throw new IllegalStateException("SETTLEMENT_APPROVAL_BINDING_CHANGED");
        }
    }

    private static BigDecimal nonnegative(BigDecimal value, int scale, String error) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > scale ||
                value.precision() - value.scale() > 12)
            throw new IllegalArgumentException(error);
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }
}
