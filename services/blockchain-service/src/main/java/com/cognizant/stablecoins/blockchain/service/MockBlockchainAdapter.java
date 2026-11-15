package com.cognizant.stablecoins.blockchain.service;

import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public class MockBlockchainAdapter implements BlockchainAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockBlockchainAdapter.class);

    private final BlockchainProperties props;

    public MockBlockchainAdapter(BlockchainProperties props) {
        this.props = props;
    }

    @Override public String getEvidenceMode() { return "MOCK"; }
    @Override public BigDecimal getGasPriceWei() { return new BigDecimal("1000000000"); }
    @Override public long getPendingNonce(String address) { return 0L; }

    @Override
    public long getChainId() {
        return props.getChainId();
    }

    @Override
    public BigDecimal getNativeBalance(String address) {
        log.info("service=blockchain-service event=mock_get_native_balance address={}", address);
        return new BigDecimal("0.5"); // 0.5 ETH mock balance
    }

    @Override
    public BigDecimal getTokenBalance(String address, String tokenContract, int decimals) {
        log.info("service=blockchain-service event=mock_get_token_balance address={}", address);
        return new BigDecimal("10000.000000"); // 10,000 USDC mock balance
    }

    @Override
    public BigDecimal estimateTransferGas(String from, String to, String tokenContract,
                                           BigDecimal amount, int decimals) {
        return new BigDecimal("65000"); // typical ERC-20 transfer gas
    }

    @Override
    public byte[] signRawTransaction(long nonce, String to, String tokenContract, BigDecimal amount,
                                      int decimals, BigDecimal gasLimit, BigDecimal gasPrice) {
        return (nonce + ":" + to + ":" + amount.toPlainString()).getBytes(java.nio.charset.StandardCharsets.UTF_8); // MOCK only
    }

    @Override
    public String submitRawTransaction(byte[] signedTx) {
        return org.web3j.utils.Numeric.toHexString(org.web3j.crypto.Hash.sha3(signedTx)); // MOCK, never real chain evidence
    }

    @Override
    public Optional<BlockchainReceiptResult> getReceipt(String txHash) {
        // MOCK immediately returns a successful receipt
        var receipt = new BlockchainReceiptResult(
            txHash, 1234567L, true,
            new BigDecimal("42000"), new BigDecimal("1000000000")
        );
        return Optional.of(receipt.fees(java.math.BigInteger.ZERO,true,null));
    }

    @Override
    public long getCurrentBlockNumber() {
        return 1234567L + Math.max(1,props.getConfirmationsRequired()) - 1; // synthetic MOCK confirmations
    }

    @Override
    public String getFromAddress() {
        return "0x0000000000000000000000000000000000000001";
    }
}
