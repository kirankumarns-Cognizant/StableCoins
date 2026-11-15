package com.cognizant.stablecoins.blockchain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blockchain_transaction")
public class BlockchainTransaction {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "payment_id", nullable = false, length = 64)
    private String paymentId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "network", nullable = false, length = 50)
    private String network;

    @Column(name = "from_address", nullable = false, length = 42)
    private String fromAddress;

    @Column(name = "to_address", nullable = false, length = 42)
    private String toAddress;

    @Column(name = "token_contract", nullable = false, length = 42)
    private String tokenContract;

    @Column(name = "token_symbol", nullable = false, length = 10)
    private String tokenSymbol;

    @Column(name = "amount", nullable = false, precision = 18, scale = 6)
    private BigDecimal amount;

    @Column(name = "amount_in_smallest", precision = 36, scale = 0)
    private BigDecimal amountInSmallest;

    @Column(name = "estimated_gas", precision = 36, scale = 0)
    private BigDecimal estimatedGas;

    @Column(name = "gas_used", precision = 36, scale = 0)
    private BigDecimal gasUsed;

    @Column(name = "effective_gas_price", precision = 36, scale = 0)
    private BigDecimal effectiveGasPrice;

    @Column(name = "nonce")
    private Long nonce;

    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PREPARED";

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "explorer_url", length = 300)
    private String explorerUrl;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "chain_id")
    private Long chainId;

    @Column(name = "gas_limit", precision = 36, scale = 0)
    private BigDecimal gasLimit;

    @Column(name = "gas_price_wei", precision = 36, scale = 0)
    private BigDecimal gasPriceWei;

    @Column(name = "gas_reserve_eth", precision = 36, scale = 18)
    private BigDecimal gasReserveEth;

    @Column(name = "reservation_status", length = 20)
    private String reservationStatus;

    @Column(name = "reservation_expires_at")
    private OffsetDateTime reservationExpiresAt;

    @Column(name = "evidence_mode", length = 20)
    private String evidenceMode;

    @PrePersist
    void prePersist() {
        if (transactionId == null) transactionId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public String getTokenContract() { return tokenContract; }
    public void setTokenContract(String tokenContract) { this.tokenContract = tokenContract; }

    public String getTokenSymbol() { return tokenSymbol; }
    public void setTokenSymbol(String tokenSymbol) { this.tokenSymbol = tokenSymbol; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getAmountInSmallest() { return amountInSmallest; }
    public void setAmountInSmallest(BigDecimal amountInSmallest) { this.amountInSmallest = amountInSmallest; }

    public BigDecimal getEstimatedGas() { return estimatedGas; }
    public void setEstimatedGas(BigDecimal estimatedGas) { this.estimatedGas = estimatedGas; }

    public BigDecimal getGasUsed() { return gasUsed; }
    public void setGasUsed(BigDecimal gasUsed) { this.gasUsed = gasUsed; }

    public BigDecimal getEffectiveGasPrice() { return effectiveGasPrice; }
    public void setEffectiveGasPrice(BigDecimal effectiveGasPrice) { this.effectiveGasPrice = effectiveGasPrice; }

    public Long getNonce() { return nonce; }
    public void setNonce(Long nonce) { this.nonce = nonce; }

    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }

    public Long getBlockNumber() { return blockNumber; }
    public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getExplorerUrl() { return explorerUrl; }
    public void setExplorerUrl(String explorerUrl) { this.explorerUrl = explorerUrl; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    @Column(precision=78,scale=0) private java.math.BigDecimal additionalFeeWei;
    @Column(precision=78,scale=0) private java.math.BigDecimal totalFeeWei;
    @Column(nullable=false) private boolean feeComponentsComplete;
    @Column(length=64) private String feeEvidenceReason;
    @Column(nullable=false) private int confirmationCount;
    public java.math.BigDecimal getAdditionalFeeWei(){return additionalFeeWei;}
    public void setAdditionalFeeWei(java.math.BigDecimal v){additionalFeeWei=v;}
    public java.math.BigDecimal getTotalFeeWei(){return totalFeeWei;}
    public void setTotalFeeWei(java.math.BigDecimal v){totalFeeWei=v;}
    public boolean isFeeComponentsComplete(){return feeComponentsComplete;}
    public void setFeeComponentsComplete(boolean v){feeComponentsComplete=v;}
    public String getFeeEvidenceReason(){return feeEvidenceReason;}
    public void setFeeEvidenceReason(String v){feeEvidenceReason=v;}
    public int getConfirmationCount(){return confirmationCount;}
    public void setConfirmationCount(int v){confirmationCount=v;}
    public Long getChainId() { return chainId; }
    public void setChainId(Long value) { chainId = value; }
    public BigDecimal getGasLimit() { return gasLimit; }
    public void setGasLimit(BigDecimal value) { gasLimit = value; }
    public BigDecimal getGasPriceWei() { return gasPriceWei; }
    public void setGasPriceWei(BigDecimal value) { gasPriceWei = value; }
    public BigDecimal getGasReserveEth() { return gasReserveEth; }
    public void setGasReserveEth(BigDecimal value) { gasReserveEth = value; }
    public String getReservationStatus() { return reservationStatus; }
    public void setReservationStatus(String value) { reservationStatus = value; }
    public OffsetDateTime getReservationExpiresAt() { return reservationExpiresAt; }
    public void setReservationExpiresAt(OffsetDateTime value) { reservationExpiresAt = value; }
    public String getEvidenceMode() { return evidenceMode; }
    public void setEvidenceMode(String value) { evidenceMode = value; }
}
