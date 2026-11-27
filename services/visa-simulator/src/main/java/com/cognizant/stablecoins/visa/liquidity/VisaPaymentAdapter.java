package com.cognizant.stablecoins.visa.liquidity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
/** Internal SIMULATED provider boundary. Fiat and token-wallet delivery are distinct capabilities. */
public interface VisaPaymentAdapter {
    enum Currency { USD, INR }
    record Balance(String providerId,Currency currency,BigDecimal total,BigDecimal reserved,BigDecimal available,
                   String evidenceMode,Instant observedAt) {}
    record Reservation(UUID reservationId,String paymentId,String correlationId,String providerId,Currency currency,
                       BigDecimal amount,String status,Instant expiresAt,String evidenceMode) {}
    record PayoutRequest(UUID executionId,String paymentId,String correlationId,String providerId,Currency currency,BigDecimal amount,UUID quoteId,int quoteVersion,String transactionHash,String scenario) {}
    com.cognizant.stablecoins.flow.LifecycleReadModels.Payout createPayout(PayoutRequest request);
    com.cognizant.stablecoins.flow.LifecycleReadModels.Payout finishPayout(UUID payoutId);
    com.cognizant.stablecoins.flow.LifecycleReadModels.Payout resolvePayout(UUID payoutId,UUID resolutionId,boolean succeeded);
    Balance getBalance(String providerId,Currency currency);
    Balance prefund(String operationId,String providerId,Currency currency,BigDecimal amount);
    Reservation reserve(String paymentId,String correlationId,String providerId,Currency currency,BigDecimal amount);
    Reservation pin(UUID reservationId);
    Reservation cancel(UUID reservationId);
    Reservation resolve(UUID reservationId,boolean consumed);
    WalletPayouts.Balance walletBalance(String providerId, com.cognizant.stablecoins.flow.SettlementNetwork.Token token);
    WalletPayouts.Balance prefundWallet(UUID operationId, String providerId,
            com.cognizant.stablecoins.flow.SettlementNetwork.Token token, BigDecimal amount);
    WalletPayouts.Payout createWalletPayout(WalletPayouts.Request request);
    WalletPayouts.Payout walletPayout(UUID payoutId);
    WalletPayouts.Statement walletPayoutStatement(UUID payoutId);
    WalletPayouts.Payout finishWalletPayout(UUID payoutId);
    WalletPayouts.Payout resolveWalletPayout(UUID payoutId, UUID resolutionId, boolean succeeded);
}
