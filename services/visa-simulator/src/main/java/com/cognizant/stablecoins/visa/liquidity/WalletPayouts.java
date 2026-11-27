package com.cognizant.stablecoins.visa.liquidity;

import com.cognizant.stablecoins.flow.FlowContract;
import com.cognizant.stablecoins.flow.SettlementNetwork;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Provider simulation only. A SUCCEEDED result is never an on-chain delivery receipt. */
public final class WalletPayouts {
    private WalletPayouts() {}
    public enum Scenario { SUCCEEDED, DECLINED, TIMEOUT }
    public enum Status { PROCESSING, UNKNOWN, SUCCEEDED, FAILED }
    public record Request(UUID executionId, String paymentId, String correlationId, String providerId,
            UUID recommendationId, int recommendationVersion, UUID quoteId, int quoteVersion,
            SettlementNetwork.Token token, String recipient, BigDecimal amount) {
        public Request {
            if (executionId == null || recommendationId == null || quoteId == null)
                throw new IllegalArgumentException("WALLET_EXECUTION_AND_REVIEW_REQUIRED");
            FlowContract.identifier(paymentId, 50);
            FlowContract.identifier(correlationId, 60);
            FlowContract.identifier(providerId, 64);
            if (recommendationVersion < 1 || quoteVersion < 1)
                throw new IllegalArgumentException("INVALID_WALLET_REVIEW_VERSION");
            recipient = SettlementNetwork.address(recipient);
            amount = quantity(token, amount);
        }
    }
    public record Balance(String providerId, SettlementNetwork.Token token, BigDecimal total,
            BigDecimal reserved, BigDecimal available, String evidenceMode, Instant observedAt) {}
    /** Immutable simulator observation at a state transition; never an external wallet balance. */
    public record BalanceChange(UUID payoutId, Status status, Balance providerBefore, Balance providerAfter,
            BigDecimal recipientBefore, BigDecimal recipientAfter, BigDecimal providerDebit,
            BigDecimal recipientCredit, String evidenceMode, Instant observedAt) {
        public BalanceChange {
            Objects.requireNonNull(payoutId); Objects.requireNonNull(status);
            if (!"SIMULATED".equals(evidenceMode)
                    || !"SIMULATED".equals(providerBefore.evidenceMode()) || !"SIMULATED".equals(providerAfter.evidenceMode())
                    || !providerBefore.providerId().equals(providerAfter.providerId())
                    || !providerBefore.token().equals(providerAfter.token())
                    || providerDebit.signum() < 0 || recipientCredit.signum() < 0 || recipientBefore.signum() < 0
                    || providerBefore.total().subtract(providerAfter.total()).compareTo(providerDebit) != 0
                    || recipientAfter.subtract(recipientBefore).compareTo(recipientCredit) != 0
                    || providerDebit.compareTo(recipientCredit) != 0
                    || (status != Status.SUCCEEDED && providerDebit.signum() != 0))
                throw new IllegalArgumentException("INVALID_SIMULATED_BALANCE_CHANGE");
        }
    }
    public record Statement(Payout payout, java.util.List<BalanceChange> changes, Balance currentProvider,
            BigDecimal recipientCreditsInThisSimulator, String evidenceMode, boolean blockchainDeliveryVerified) {}
    public record Payout(UUID payoutId, Request request, String providerReference, Status status,
            Scenario scenario, UUID resolutionId, String evidenceMode, boolean blockchainDeliveryVerified,
            String transactionHash, Instant createdAt, Instant updatedAt) {
        public Payout {
            Objects.requireNonNull(payoutId);
            Objects.requireNonNull(request);
            Objects.requireNonNull(status);
            Objects.requireNonNull(scenario);
            if (!"SIMULATED".equals(evidenceMode) || blockchainDeliveryVerified || transactionHash != null
                    || !("SIMULATED-VISA-WALLET-" + payoutId).equals(providerReference))
                throw new IllegalArgumentException("WALLET_SIMULATION_EVIDENCE_REQUIRED");
        }
    }
    public static BigDecimal quantity(SettlementNetwork.Token token, BigDecimal amount) {
        if (token == null) throw new IllegalArgumentException("WALLET_TOKEN_REQUIRED");
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > token.decimals()
                || amount.precision() - (long) amount.scale() > 22)
            throw new IllegalArgumentException("INVALID_SIMULATED_TOKEN_AMOUNT");
        return amount.stripTrailingZeros();
    }
}