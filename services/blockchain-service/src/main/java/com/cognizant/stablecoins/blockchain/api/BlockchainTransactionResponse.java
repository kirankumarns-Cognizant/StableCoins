package com.cognizant.stablecoins.blockchain.api;

import com.cognizant.stablecoins.blockchain.model.BlockchainTransaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST response for blockchain transaction state.
 * NOTE: private key NEVER appears here — only wallet address and txHash.
 */
public class BlockchainTransactionResponse {

    private UUID transactionId;
    private String paymentId;
    private String status;
    private String txHash;
    private Long blockNumber;
    private String explorerUrl;
    private BigDecimal amount;
    private String tokenSymbol;
    private String fromAddress;
    private String toAddress;
    private String gasUsed;
    private String network;

    private String correlationId;
    private Long chainId;
    private String tokenContract;
    private String evidenceMode;
    private String reservationStatus;
    private BigDecimal gasReserveEth;
    private BigDecimal effectiveGasPriceWei,additionalFeeWei,totalFeeWei;
    private boolean feeComponentsComplete;
    private String feeEvidenceReason;
    private int confirmationCount;
    public String getEffectiveGasPriceWei(){return effectiveGasPriceWei==null?null:effectiveGasPriceWei.toPlainString();}
    public String getAdditionalFeeWei(){return additionalFeeWei==null?null:additionalFeeWei.toPlainString();}
    public String getTotalFeeWei(){return totalFeeWei==null?null:totalFeeWei.toPlainString();}
    public boolean isFeeComponentsComplete(){return feeComponentsComplete;}
    public String getFeeEvidenceReason(){return feeEvidenceReason;}
    public int getConfirmationCount(){return confirmationCount;}
    public String getCorrelationId() { return correlationId; }
    public Long getChainId() { return chainId; }
    public String getTokenContract() { return tokenContract; }
    public String getEvidenceMode() { return evidenceMode; }
    public String getReservationStatus() { return reservationStatus; }
    public BigDecimal getGasReserveEth() { return gasReserveEth; }

    public static BlockchainTransactionResponse from(BlockchainTransaction tx) {
        var r = new BlockchainTransactionResponse();
        r.transactionId = tx.getTransactionId();
        r.paymentId = tx.getPaymentId();r.correlationId=tx.getCorrelationId();r.chainId=tx.getChainId();
        r.tokenContract=tx.getTokenContract();r.evidenceMode=tx.getEvidenceMode();
        r.reservationStatus=tx.getReservationStatus();r.gasReserveEth=tx.getGasReserveEth();
        r.status = tx.getStatus();
        r.txHash = tx.getTxHash();
        r.blockNumber = tx.getBlockNumber();
        r.explorerUrl = tx.getExplorerUrl();
        r.amount = tx.getAmount();
        r.tokenSymbol = tx.getTokenSymbol();
        r.fromAddress = tx.getFromAddress();
        r.toAddress = tx.getToAddress();
        r.gasUsed = tx.getGasUsed() != null ? tx.getGasUsed().toPlainString() : null;
        r.network = tx.getNetwork();
        r.effectiveGasPriceWei=tx.getEffectiveGasPrice();r.additionalFeeWei=tx.getAdditionalFeeWei();r.totalFeeWei=tx.getTotalFeeWei();r.feeComponentsComplete=tx.isFeeComponentsComplete();r.feeEvidenceReason=tx.getFeeEvidenceReason();r.confirmationCount=tx.getConfirmationCount();
        return r;
    }

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }

    public Long getBlockNumber() { return blockNumber; }
    public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }

    public String getExplorerUrl() { return explorerUrl; }
    public void setExplorerUrl(String explorerUrl) { this.explorerUrl = explorerUrl; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getTokenSymbol() { return tokenSymbol; }
    public void setTokenSymbol(String tokenSymbol) { this.tokenSymbol = tokenSymbol; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public String getGasUsed() { return gasUsed; }
    public void setGasUsed(String gasUsed) { this.gasUsed = gasUsed; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }
}
