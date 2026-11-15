package com.cognizant.stablecoins.blockchain.service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
/** Test assets only. The ETH reserve includes a configured safety allowance, not a market USD quote. */
public record TreasuryReadiness(String evidenceMode, String network, long chainId, String tokenContract,
        String walletAddress, BigDecimal totalUsdc, BigDecimal reservedUsdc, BigDecimal availableUsdc,
        BigDecimal totalEth, BigDecimal reservedEth, BigDecimal availableEth,
        BigDecimal estimatedGas, BigDecimal gasLimit, BigDecimal gasPriceWei, BigDecimal requiredGasEth,
        Instant observedAt, Instant expiresAt, List<String> reasons) {
    public TreasuryReadiness { reasons=List.copyOf(reasons); }
    public boolean ready() { return reasons.isEmpty(); }
}
