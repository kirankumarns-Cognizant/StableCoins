package com.cognizant.stablecoins.blockchain.service;

import java.math.BigDecimal;

public class BlockchainReceiptResult {

    private final String txHash;
    private final Long blockNumber;
    private final boolean success;
    private final BigDecimal gasUsed;
    private final BigDecimal effectiveGasPrice;

    public BlockchainReceiptResult(String txHash, Long blockNumber, boolean success,
                                    BigDecimal gasUsed, BigDecimal effectiveGasPrice) {
        this.txHash = txHash;
        this.blockNumber = blockNumber;
        this.success = success;
        this.gasUsed = gasUsed;
        this.effectiveGasPrice = effectiveGasPrice;
    }

    private java.math.BigInteger additionalFeeWei;
    private java.math.BigInteger totalFeeWei;
    private boolean feeComponentsComplete;
    private String feeEvidenceReason = "FEE_EVIDENCE_INCOMPLETE";
    public BlockchainReceiptResult fees(java.math.BigInteger additional, boolean complete, String reason) {
        additionalFeeWei=additional; feeComponentsComplete=complete; feeEvidenceReason=reason;
        if(complete && gasUsed!=null && effectiveGasPrice!=null && additional!=null)
            totalFeeWei=gasUsed.toBigIntegerExact().multiply(effectiveGasPrice.toBigIntegerExact()).add(additional);
        return this;
    }
    public java.math.BigInteger getAdditionalFeeWei(){return additionalFeeWei;}
    public java.math.BigInteger getTotalFeeWei(){return totalFeeWei;}
    public boolean isFeeComponentsComplete(){return feeComponentsComplete;}
    public String getFeeEvidenceReason(){return feeEvidenceReason;}
    public String getTxHash() { return txHash; }
    public Long getBlockNumber() { return blockNumber; }
    public boolean isSuccess() { return success; }
    public BigDecimal getGasUsed() { return gasUsed; }
    public BigDecimal getEffectiveGasPrice() { return effectiveGasPrice; }
}
