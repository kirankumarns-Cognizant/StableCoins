package com.cognizant.stablecoins.blockchain.service;

import java.math.BigDecimal;
import java.util.Optional;

public interface BlockchainAdapter {

    long getChainId();

    String getEvidenceMode();

    BigDecimal getGasPriceWei();

    long getPendingNonce(String address);

    BigDecimal getNativeBalance(String address);

    BigDecimal getTokenBalance(String address, String tokenContract, int decimals);

    BigDecimal estimateTransferGas(String from, String to, String tokenContract,
                                    BigDecimal amount, int decimals);

    byte[] signRawTransaction(long nonce, String to, String tokenContract, BigDecimal amount,
                               int decimals, BigDecimal gasLimit, BigDecimal gasPrice);

    String submitRawTransaction(byte[] signedTx);

    Optional<BlockchainReceiptResult> getReceipt(String txHash);

    long getCurrentBlockNumber();

    String getFromAddress();
}
