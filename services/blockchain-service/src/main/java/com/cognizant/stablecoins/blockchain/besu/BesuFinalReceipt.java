package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.SettlementNetwork;
import java.math.*;
import java.util.*;

/** Immutable execution-only receipt evidence. Not an application ledger posting. */
record BesuFinalReceipt(UUID executionId,String transactionHash,String blockHash,long blockNumber,
                        boolean success,BigDecimal gasUsed,BigDecimal gasPriceWei,BigInteger totalFeeWei) {
    BesuFinalReceipt {
        Objects.requireNonNull(executionId);
        transactionHash=SettlementNetwork.hash(transactionHash); blockHash=SettlementNetwork.hash(blockHash);
        if (blockNumber<=0 || gasUsed==null || gasPriceWei==null || totalFeeWei==null
                || gasUsed.signum()<=0 || gasUsed.compareTo(new BigDecimal("30000000"))>0
                || gasPriceWei.signum()<0 || gasPriceWei.toBigIntegerExact().bitLength()>128
                || !gasUsed.toBigIntegerExact().multiply(gasPriceWei.toBigIntegerExact()).equals(totalFeeWei))
            throw new IllegalArgumentException("BESU_FINAL_RECEIPT_INVALID");
    }
    String status() { return success ? "CONFIRMED" : "REVERTED"; }
    void requireFor(BesuSignedTransfer t) {
        if (!executionId.equals(t.execution.executionId()) || !transactionHash.equals(t.hash)
                || gasUsed.compareTo(t.gasLimit)>0 || gasPriceWei.compareTo(t.gasPrice)!=0)
            throw new IllegalStateException("BESU_FINAL_RECEIPT_BINDING_CONFLICT");
    }
}
