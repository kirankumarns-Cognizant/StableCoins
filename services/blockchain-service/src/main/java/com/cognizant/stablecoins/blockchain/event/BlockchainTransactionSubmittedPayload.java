package com.cognizant.stablecoins.blockchain.event;

import java.math.BigDecimal;
import java.util.UUID;

public class BlockchainTransactionSubmittedPayload {

    private UUID transactionId;
    private String network;
    private String txHash;
    private String fromAddress;
    private String toAddress;
    private BigDecimal amount;
    private String tokenSymbol;
    private String explorerUrl;

    public BlockchainTransactionSubmittedPayload() {}

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }

    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getTokenSymbol() { return tokenSymbol; }
    public void setTokenSymbol(String tokenSymbol) { this.tokenSymbol = tokenSymbol; }

    public String getExplorerUrl() { return explorerUrl; }
    public void setExplorerUrl(String explorerUrl) { this.explorerUrl = explorerUrl; }
    private Long chainId;
    private String tokenContract;
    private String evidenceMode;
    public Long getChainId() { return chainId; }
    public void setChainId(Long v) { chainId=v; }
    public String getTokenContract() { return tokenContract; }
    public void setTokenContract(String v) { tokenContract=v; }
    public String getEvidenceMode() { return evidenceMode; }
    public void setEvidenceMode(String v) { evidenceMode=v; }
}
