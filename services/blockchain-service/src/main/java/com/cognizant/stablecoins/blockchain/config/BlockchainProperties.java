package com.cognizant.stablecoins.blockchain.config;

import com.cognizant.stablecoins.blockchain.enums.BlockchainMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {

    private BlockchainMode mode = BlockchainMode.MOCK;
    private String network = "base-sepolia";
    private long chainId = 84532L;
    private String rpcUrl = "";
    private String mockRpcUrl = "http://localhost:8545";
    private int rpcConnectTimeoutMs = 10000;
    private int rpcReadTimeoutMs = 30000;

    public String getMockRpcUrl() { return mockRpcUrl; }
    public void setMockRpcUrl(String value) { mockRpcUrl = value; }
    public int getRpcConnectTimeoutMs() { return rpcConnectTimeoutMs; }
    public void setRpcConnectTimeoutMs(int value) {
        if (value <= 0) throw new IllegalArgumentException("RPC connect timeout must be positive");
        rpcConnectTimeoutMs = value;
    }
    public int getRpcReadTimeoutMs() { return rpcReadTimeoutMs; }
    public void setRpcReadTimeoutMs(int value) {
        if (value <= 0) throw new IllegalArgumentException("RPC read timeout must be positive");
        rpcReadTimeoutMs = value;
    }
    private String tokenContract = "0x036CbD53842c5426634e7929541eC2318f3dCF7e";
    private String tokenSymbol = "USDC";
    private int tokenDecimals = 6;
    private String explorerBaseUrl = "https://sepolia.basescan.org";
    private int confirmationsRequired = 1;

    public BlockchainMode getMode() { return mode; }
    public void setMode(BlockchainMode mode) { this.mode = mode; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }

    public long getChainId() { return chainId; }
    public void setChainId(long chainId) { this.chainId = chainId; }

    public String getRpcUrl() { return rpcUrl; }
    public void setRpcUrl(String rpcUrl) { this.rpcUrl = rpcUrl; }

    public String getTokenContract() { return tokenContract; }
    public void setTokenContract(String tokenContract) { this.tokenContract = tokenContract; }

    public String getTokenSymbol() { return tokenSymbol; }
    public void setTokenSymbol(String tokenSymbol) { this.tokenSymbol = tokenSymbol; }

    public int getTokenDecimals() { return tokenDecimals; }
    public void setTokenDecimals(int tokenDecimals) { this.tokenDecimals = tokenDecimals; }

    public String getExplorerBaseUrl() { return explorerBaseUrl; }
    public void setExplorerBaseUrl(String explorerBaseUrl) { this.explorerBaseUrl = explorerBaseUrl; }

    public int getConfirmationsRequired() { return confirmationsRequired; }
    public void setConfirmationsRequired(int confirmationsRequired) { this.confirmationsRequired = confirmationsRequired; }

    private java.time.Duration reservationTtl = java.time.Duration.ofMinutes(5);
    private java.time.Duration readinessTtl = java.time.Duration.ofSeconds(30);
    private java.math.BigDecimal gasLimitMultiplier = new java.math.BigDecimal("1.2");
    private java.math.BigDecimal gasPriceMultiplier = new java.math.BigDecimal("1.2");
    private java.math.BigDecimal additionalGasReserveEth = new java.math.BigDecimal("0.00001");
    public java.time.Duration getReservationTtl() { return reservationTtl; }
    public void setReservationTtl(java.time.Duration v) { reservationTtl = validDuration(v); }
    public java.time.Duration getReadinessTtl() { return readinessTtl; }
    public void setReadinessTtl(java.time.Duration v) { readinessTtl = validDuration(v); }
    private java.time.Duration validDuration(java.time.Duration v) {
        if(v==null || v.isNegative() || v.isZero() || v.compareTo(java.time.Duration.ofHours(24))>0)
            throw new IllegalArgumentException("Treasury duration must be positive and at most 24 hours");
        return v;
    }
    public java.math.BigDecimal getGasLimitMultiplier() { return gasLimitMultiplier; }
    public void setGasLimitMultiplier(java.math.BigDecimal v) { gasLimitMultiplier = validMultiplier(v); }
    public java.math.BigDecimal getGasPriceMultiplier() { return gasPriceMultiplier; }
    public void setGasPriceMultiplier(java.math.BigDecimal v) { gasPriceMultiplier = validMultiplier(v); }
    private java.math.BigDecimal validMultiplier(java.math.BigDecimal v) {
        if(v==null || v.compareTo(java.math.BigDecimal.ONE)<0 || v.compareTo(java.math.BigDecimal.TEN)>0 || v.scale()>4)
            throw new IllegalArgumentException("Gas multiplier must be between 1 and 10 with at most 4 decimals");
        return v;
    }
    public java.math.BigDecimal getAdditionalGasReserveEth() { return additionalGasReserveEth; }
    public void setAdditionalGasReserveEth(java.math.BigDecimal v) {
        if(v==null || v.signum()<0 || v.compareTo(java.math.BigDecimal.ONE)>0 || v.scale()>18)
            throw new IllegalArgumentException("Additional test ETH reserve must be between 0 and 1 with at most 18 decimals");
        additionalGasReserveEth=v;
    }
}
