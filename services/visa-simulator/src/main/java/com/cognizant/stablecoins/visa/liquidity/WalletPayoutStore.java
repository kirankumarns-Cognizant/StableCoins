package com.cognizant.stablecoins.visa.liquidity;

import com.cognizant.stablecoins.flow.SettlementNetwork;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Every call executes inside the adapter transaction. Lock before read/modify/write. */
public interface WalletPayoutStore {
    void lockPayment(String paymentId);
    boolean hasFiatPayout(String paymentId);
    WalletPayouts.Balance lockBalance(String provider, SettlementNetwork.Token token);
    Optional<WalletPayouts.Payout> byPayment(String paymentId);
    WalletPayouts.Payout get(UUID id);
    void prefund(UUID operationId, String provider, SettlementNetwork.Token token, BigDecimal amount);
    BigDecimal recipientCredits(String provider, SettlementNetwork.Token token, String recipient);
    void recordBalanceChange(WalletPayouts.BalanceChange change);
    java.util.List<WalletPayouts.BalanceChange> balanceChanges(UUID payoutId);
    void insert(WalletPayouts.Payout payout);
    void transition(WalletPayouts.Payout payout, boolean consume);
}