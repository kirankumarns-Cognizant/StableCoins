package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.blockchain.service.*;
import com.cognizant.stablecoins.flow.SettlementNetwork;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.time.*;
import java.util.*;
import org.web3j.abi.*;
import org.web3j.abi.datatypes.*;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

/**
 * Real PRIVATE_DEMO adapter. No deployment, retry, fallback or persistence side effects.
 * The transaction owner must durably claim signed hash/nonce BEFORE calling submit.
 * Not a default BlockchainAdapter bean: legacy USDC execution cannot select this path.
 */
public final class BesuBlockchainAdapter implements BlockchainAdapter {
    private static final String TRANSFER = "0xa9059cbb";
    private static final String TRANSFER_TOPIC = Hash.sha3String("Transfer(address,address,uint256)");
    private final BesuSettings settings;
    private final BesuRpc rpc;
    private final WalletSigningService signer;
    private final Clock clock;

    public BesuBlockchainAdapter(BesuSettings settings, BesuRpc rpc, WalletSigningService signer, Clock clock) {
        this.settings = Objects.requireNonNull(settings);
        this.rpc = Objects.requireNonNull(rpc);
        this.signer = Objects.requireNonNull(signer);
        this.clock = Objects.requireNonNull(clock);
        if (!settings.context().treasury().equals(SettlementNetwork.address(signer.getWalletAddress())))
            throw new IllegalArgumentException("BESU_SIGNER_ADDRESS_MISMATCH");
    }
    public SettlementNetwork.Context context() { return settings.context(); }
    @Override public String getEvidenceMode() { return "LIVE"; }
    public String classification() { return "PRIVATE_DEMO"; }
    @Override public String getFromAddress() { return context().treasury(); }

    /** Rechecks durable identity, code, metadata, validator set and a recent non-genesis head. */
    public void verifyReadiness() { verifyIdentity(true); }

    private void verifyIdentity(boolean fresh) {
        if (!quantity(rpc.call("eth_chainId", List.of())).equals(BigInteger.valueOf(BesuSettings.CHAIN_ID)))
            fail("BESU_CHAIN_MISMATCH");
        var genesis = rpc.call("eth_getBlockByNumber", List.of("0x0", false));
        if (!context().token().chain().genesisHash().equals(hash(genesis.path("hash"))))
            fail("BESU_GENESIS_MISMATCH");
        var validators = rpc.call("qbft_getValidatorsByBlockNumber", List.of("latest"));
        if (!validators.isArray() || validators.size() != 4) fail("BESU_VALIDATORS_MISMATCH");
        var actual = new HashSet<String>();
        for (var validator : validators) actual.add(address(validator));
        if (!actual.equals(settings.validators())) fail("BESU_VALIDATORS_MISMATCH");
        var code = rpc.call("eth_getCode", List.of(context().token().contract(), "latest"));
        String bytes = text(code);
        if (!bytes.matches("0x(?:[0-9a-fA-F]{2})+") || !context().token().codeHash().equalsIgnoreCase(Hash.sha3(bytes)))
            fail("BESU_TOKEN_CODE_MISMATCH");
        if (!quantityResult("0x313ce567").equals(BigInteger.valueOf(6))
                || !quantityResult("0x18160ddd").equals(BesuSettings.SUPPLY))
            fail("BESU_TOKEN_METADATA_MISMATCH");
        try {
            var symbol = FunctionReturnDecoder.decode(tokenCall("0x95d89b41"),
                    new Function("symbol", List.of(), List.of(new TypeReference<Utf8String>() {})).getOutputParameters());
            if (symbol.size() != 1 || !"DEMOUSD".equals(symbol.getFirst().getValue()))
                fail("BESU_TOKEN_METADATA_MISMATCH");
        } catch (RuntimeException ignored) { fail("BESU_TOKEN_METADATA_MISMATCH"); }
        if (fresh) freshHead();
    }
    private long freshHead() {
        var block = rpc.call("eth_getBlockByNumber", List.of("latest", false));
        long number = quantity(block.path("number")).longValueExact();
        long timestamp = quantity(block.path("timestamp")).longValueExact();
        hash(block.path("hash"));
        Instant observed;
        try { observed = Instant.ofEpochSecond(timestamp); }
        catch (RuntimeException ignored) { throw failure("BESU_HEAD_INVALID"); }
        if (number <= 0 || observed.isAfter(clock.instant().plusSeconds(5))
                || observed.isBefore(clock.instant().minus(settings.maximumHeadAge())))
            fail("BESU_HEAD_STALE_OR_HALTED");
        return number;
    }
    @Override public long getChainId() {
        verifyIdentity(true);
        return BesuSettings.CHAIN_ID;
    }
    @Override public long getCurrentBlockNumber() {
        verifyIdentity(false);
        return freshHead();
    }
    @Override public BigDecimal getGasPriceWei() {
        verifyIdentity(true);
        BigInteger price = quantity(rpc.call("eth_gasPrice", List.of()));
        if (price.compareTo(settings.minimumGasPrice()) < 0) fail("BESU_GAS_PRICE_POLICY_MISMATCH");
        return new BigDecimal(price);
    }
    @Override public long getPendingNonce(String address) {
        if (!getFromAddress().equals(SettlementNetwork.address(address))) fail("BESU_SENDER_MISMATCH");
        verifyIdentity(true);
        return quantity(rpc.call("eth_getTransactionCount", List.of(getFromAddress(), "pending"))).longValueExact();
    }
    @Override public BigDecimal getNativeBalance(String address) {
        String wallet = participant(address);
        verifyIdentity(true);
        return new BigDecimal(quantity(rpc.call("eth_getBalance", List.of(wallet, "latest")))).movePointLeft(18);
    }
    @Override public BigDecimal getTokenBalance(String address, String tokenContract, int decimals) {
        settings.token(tokenContract, decimals);
        String wallet = participant(address);
        verifyIdentity(true);
        return new BigDecimal(balance(wallet)).movePointLeft(6);
    }
    private BigInteger balance(String wallet) {
        return quantityResult("0x70a08231" + "0".repeat(24) + wallet.substring(2));
    }
    @Override public BigDecimal estimateTransferGas(String from, String to, String tokenContract,
                                                   BigDecimal amount, int decimals) {
        if (!getFromAddress().equals(SettlementNetwork.address(from))) fail("BESU_SENDER_MISMATCH");
        settings.token(tokenContract, decimals);
        String data = transferData(to, amount);
        verifyIdentity(true);
        return new BigDecimal(estimate(data));
    }
    private BigInteger estimate(String data) {
        BigInteger gas = quantity(rpc.call("eth_estimateGas", List.of(Map.of(
                "from", getFromAddress(), "to", context().token().contract(), "value", "0x0", "data", data))));
        if (gas.signum() <= 0 || gas.compareTo(BigInteger.valueOf(30_000_000)) > 0)
            fail("BESU_GAS_ESTIMATE_INVALID");
        return gas;
    }
    @Override public byte[] signRawTransaction(long nonce, String to, String tokenContract, BigDecimal amount,
                                               int decimals, BigDecimal gasLimit, BigDecimal gasPrice) {
        settings.token(tokenContract, decimals);
        String data = transferData(to, amount);
        BigInteger limit = integer(gasLimit), price = integer(gasPrice);
        if (nonce < 0 || limit.signum() <= 0 || limit.compareTo(BigInteger.valueOf(30_000_000)) > 0
                || price.compareTo(settings.minimumGasPrice()) < 0 || price.bitLength() > 128)
            throw new IllegalArgumentException("BESU_SIGNING_PARAMETERS_INVALID");
        verifyIdentity(true);
        if (limit.compareTo(estimate(data)) < 0) fail("BESU_GAS_LIMIT_INSUFFICIENT");
        if (balance(getFromAddress()).compareTo(amount.movePointRight(6).toBigIntegerExact()) < 0)
            fail("BESU_TOKEN_BALANCE_INSUFFICIENT");
        if (quantity(rpc.call("eth_getBalance", List.of(getFromAddress(), "latest")))
                .compareTo(limit.multiply(price)) < 0) fail("BESU_GAS_BALANCE_INSUFFICIENT");
        var raw = RawTransaction.createTransaction(BigInteger.valueOf(nonce), price, limit,
                context().token().contract(), BigInteger.ZERO, data);
        byte[] signed = signer.signTransaction(raw, BesuSettings.CHAIN_ID);
        var decoded = signedTransaction(signed);
        if (!decoded.getNonce().equals(raw.getNonce()) || !decoded.getGasLimit().equals(limit)
                || !decoded.getGasPrice().equals(price) || !Numeric.prependHexPrefix(decoded.getData()).equalsIgnoreCase(data))
            fail("BESU_SIGNER_TRANSACTION_MISMATCH");
        return signed;
    }
    /** Single attempt only. Durable uncertainty handling belongs to the existing transaction owner. */
    @Override public String submitRawTransaction(byte[] signedTx) {
        signedTransaction(signedTx);
        verifyIdentity(true);
        String expected = Hash.sha3(Numeric.toHexString(signedTx));
        String actual = hash(rpc.call("eth_sendRawTransaction", List.of(Numeric.toHexString(signedTx))));
        if (!expected.equalsIgnoreCase(actual)) fail("BESU_SUBMISSION_HASH_MISMATCH");
        return expected;
    }
    /** Recovery only: presence proves submission, never successful execution or finality. */
    boolean observesTransaction(byte[] bytes) {
        var expected=signedTransaction(bytes);
        verifyIdentity(false);
        String expectedHash=Hash.sha3(Numeric.toHexString(bytes));
        var tx=rpc.call("eth_getTransactionByHash",List.of(expectedHash));
        if (tx==null || tx.isNull()) return false;
        requireSignedTransaction(expected,expectedHash,tx);
        return true;
    }
    private void requireSignedTransaction(SignedRawTransaction expected,String expectedHash,JsonNode tx) {
        if (tx==null || tx.isNull()) fail("BESU_RECOVERY_TRANSACTION_MISSING");
        if (!expectedHash.equals(hash(tx.path("hash")))
                || !getFromAddress().equals(address(tx.path("from")))
                || !context().token().contract().equals(address(tx.path("to")))
                || !BigInteger.valueOf(BesuSettings.CHAIN_ID).equals(quantity(tx.path("chainId")))
                || !expected.getNonce().equals(quantity(tx.path("nonce")))
                || !expected.getValue().equals(quantity(tx.path("value")))
                || !expected.getGasLimit().equals(quantity(tx.path("gas")))
                || !expected.getGasPrice().equals(quantity(tx.path("gasPrice")))
                || !Numeric.prependHexPrefix(expected.getData()).equalsIgnoreCase(text(tx.path("input"))))
            fail("BESU_RECOVERY_TRANSACTION_MISMATCH");
    }

    private SignedRawTransaction signedTransaction(byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0 || bytes.length > 4096) throw new IllegalArgumentException();
            var decoded = TransactionDecoder.decode(Numeric.toHexString(bytes));
            if (!(decoded instanceof SignedRawTransaction signed) || !signed.getType().isLegacy()
                    || !Long.valueOf(BesuSettings.CHAIN_ID).equals(signed.getChainId())
                    || !getFromAddress().equalsIgnoreCase(signed.getFrom())
                    || !context().token().contract().equalsIgnoreCase(signed.getTo())
                    || signed.getValue().signum() != 0 || signed.getNonce().signum() < 0
                    || signed.getNonce().bitLength() > 63 || signed.getGasLimit().signum() <= 0
                    || signed.getGasLimit().compareTo(BigInteger.valueOf(30_000_000)) > 0
                    || signed.getGasPrice().compareTo(settings.minimumGasPrice()) < 0
                    || signed.getGasPrice().bitLength() > 128) throw new IllegalArgumentException();
            decodeTransfer(Numeric.prependHexPrefix(signed.getData()));
            return signed;
        } catch (Exception ignored) { throw new IllegalArgumentException("BESU_SIGNED_TRANSACTION_CONTEXT_MISMATCH"); }
    }
    /** Exact saved payload plus canonical QBFT receipt; no fresh-head requirement for recovery. */
    Optional<BesuFinalReceipt> finalReceipt(BesuSignedTransfer saved) {
        if (!context().equals(saved.execution.context())) fail("BESU_RECEIPT_CONTEXT_MISMATCH");
        var blockHash=new String[1];
        var receipt=readReceipt(saved.hash,signedTransaction(saved.payload()),blockHash);
        if (receipt.isEmpty()) return Optional.empty();
        var r=receipt.get();
        return Optional.of(new BesuFinalReceipt(saved.execution.executionId(),saved.hash,blockHash[0],
                r.getBlockNumber(),r.isSuccess(),r.getGasUsed(),r.getEffectiveGasPrice(),r.getTotalFeeWei()));
    }
    @Override public Optional<BlockchainReceiptResult> getReceipt(String txHash) {
        return readReceipt(txHash,null,new String[1]);
    }
    private Optional<BlockchainReceiptResult> readReceipt(String txHash,SignedRawTransaction signed,String[] capturedBlock) {
        String expectedHash = SettlementNetwork.hash(txHash);
        verifyIdentity(false); // Recovery may inspect a mined receipt even if block production stopped.
        var receipt = rpc.call("eth_getTransactionReceipt", List.of(expectedHash));
        if (receipt == null || receipt.isNull()) return Optional.empty();
        if (!expectedHash.equals(hash(receipt.path("transactionHash")))) fail("BESU_RECEIPT_MISMATCH");
        long block = quantity(receipt.path("blockNumber")).longValueExact();
        String blockHash = hash(receipt.path("blockHash"));
        var canonical = rpc.call("eth_getBlockByNumber", List.of(Numeric.encodeQuantity(BigInteger.valueOf(block)), false));
        if (block <= 0 || !blockHash.equals(hash(canonical.path("hash")))) fail("BESU_RECEIPT_NOT_CANONICAL");
        var tx = rpc.call("eth_getTransactionByHash", List.of(expectedHash));
        if (!expectedHash.equals(hash(tx.path("hash"))) || !getFromAddress().equals(address(tx.path("from")))
                || !context().token().contract().equals(address(tx.path("to")))
                || !quantity(tx.path("chainId")).equals(BigInteger.valueOf(BesuSettings.CHAIN_ID))
                || quantity(tx.path("value")).signum() != 0
                || !blockHash.equals(hash(tx.path("blockHash")))
                || quantity(tx.path("blockNumber")).longValueExact() != block
                || !getFromAddress().equals(address(receipt.path("from")))
                || !context().token().contract().equals(address(receipt.path("to"))))
            fail("BESU_RECEIPT_TRANSACTION_MISMATCH");
        if (signed!=null) requireSignedTransaction(signed,expectedHash,tx);
        Transfer transfer = decodeTransfer(text(tx.path("input")));
        BigInteger status = quantity(receipt.path("status"));
        if (!Set.of(BigInteger.ZERO, BigInteger.ONE).contains(status)) fail("BESU_RECEIPT_STATUS_INVALID");
        if (status.equals(BigInteger.ONE)) verifyTransferLog(receipt, expectedHash, blockHash, block, transfer);
        else if (!receipt.path("logs").isArray() || !receipt.path("logs").isEmpty()) fail("BESU_REVERTED_LOGS_INVALID");
        var gas = quantity(receipt.path("gasUsed"));
        if (gas.signum() <= 0) fail("BESU_RECEIPT_GAS_INVALID");
        // This chain is execution-only. Reject unexpected non-zero rollup fee components.
        for (String field : List.of("l1Fee", "operatorFee", "operatorFeeScalar", "operatorFeeConstant"))
            if (receipt.hasNonNull(field) && quantity(receipt.path(field)).signum() != 0)
                fail("BESU_UNEXPECTED_FEE_COMPONENT");
        JsonNode priceNode = receipt.get("effectiveGasPrice");
        BigInteger price = priceNode == null || priceNode.isNull() ? null : quantity(priceNode);
        if (signed!=null && (price==null || !price.equals(signed.getGasPrice()) || gas.compareTo(signed.getGasLimit())>0))
            fail("BESU_RECEIPT_SIGNED_FEE_MISMATCH");
        // Recheck canonical identity after the transaction/log/fee reads. QBFT commits are final.
        var checked=rpc.call("eth_getBlockByNumber",List.of(Numeric.encodeQuantity(BigInteger.valueOf(block)),false));
        if (!blockHash.equals(hash(checked.path("hash")))) fail("BESU_RECEIPT_NOT_CANONICAL");
        capturedBlock[0]=blockHash;
        var result = new BlockchainReceiptResult(expectedHash, block, status.equals(BigInteger.ONE),
                new BigDecimal(gas), price == null ? null : new BigDecimal(price));
        return Optional.of(result.fees(price == null ? null : BigInteger.ZERO, price != null,
                price == null ? "EXECUTION_FEE_MISSING" : null));
    }
    private void verifyTransferLog(JsonNode receipt, String txHash, String blockHash, long block, Transfer transfer) {
        JsonNode logs = receipt.path("logs");
        if (!logs.isArray()) fail("BESU_TRANSFER_LOG_MISSING");
        int count = 0;
        for (var log : logs) {
            if (!context().token().contract().equalsIgnoreCase(log.path("address").asText())) continue;
            var topics = log.path("topics");
            if (!topics.isArray() || topics.isEmpty() || !TRANSFER_TOPIC.equalsIgnoreCase(topics.get(0).asText())) continue;
            if (topics.size() != 3 || !topicAddress(topics.get(1)).equals(getFromAddress())
                    || !topicAddress(topics.get(2)).equals(transfer.recipient())
                    || !quantity(log.path("data")).equals(transfer.amount())
                    || !txHash.equals(hash(log.path("transactionHash")))
                    || !blockHash.equals(hash(log.path("blockHash")))
                    || quantity(log.path("blockNumber")).longValueExact() != block
                    || !log.path("removed").isBoolean() || log.path("removed").booleanValue())
                fail("BESU_TRANSFER_LOG_MISMATCH");
            count++;
        }
        if (count != 1) fail("BESU_TRANSFER_LOG_COUNT_MISMATCH");
    }
    /** Validate observation inputs before making any RPC calls. */
    void validateObservationRequest(String recipient, BigDecimal amount) { transferData(recipient, amount); }

    private String transferData(String to, BigDecimal amount) {
        String recipient = settings.recipient(to);
        BigInteger units;
        try { units = Objects.requireNonNull(amount).movePointRight(6).toBigIntegerExact(); }
        catch (RuntimeException ignored) { throw new IllegalArgumentException("BESU_TOKEN_AMOUNT_INVALID"); }
        if (units.signum() <= 0 || units.compareTo(BesuSettings.SUPPLY) > 0)
            throw new IllegalArgumentException("BESU_TOKEN_AMOUNT_INVALID");
        return TRANSFER + "0".repeat(24) + recipient.substring(2) + Numeric.toHexStringNoPrefixZeroPadded(units, 64);
    }
    private Transfer decodeTransfer(String data) {
        if (data == null || !data.matches("(?i)0xa9059cbb0{24}[0-9a-f]{104}"))
            throw new IllegalArgumentException("BESU_TRANSFER_INPUT_INVALID");
        String recipient = settings.recipient("0x" + data.substring(34, 74));
        BigInteger amount = new BigInteger(data.substring(74), 16);
        if (amount.signum() <= 0 || amount.compareTo(BesuSettings.SUPPLY) > 0)
            throw new IllegalArgumentException("BESU_TOKEN_AMOUNT_INVALID");
        return new Transfer(recipient, amount);
    }
    private String tokenCall(String data) {
        return text(rpc.call("eth_call", List.of(Map.of("to", context().token().contract(), "data", data), "latest")));
    }
    private BigInteger quantityResult(String data) { return quantityText(tokenCall(data)); }
    private String participant(String wallet) {
        String value = SettlementNetwork.address(wallet);
        return getFromAddress().equals(value) ? value : settings.recipient(value);
    }
    private static BigInteger integer(BigDecimal value) {
        try { return Objects.requireNonNull(value).toBigIntegerExact(); }
        catch (RuntimeException ignored) { throw new IllegalArgumentException("BESU_INTEGER_REQUIRED"); }
    }
    private static String topicAddress(JsonNode value) {
        String topic = text(value);
        if (!topic.matches("0x0{24}[0-9a-fA-F]{40}")) throw failure("BESU_TRANSFER_TOPIC_INVALID");
        return SettlementNetwork.address("0x" + topic.substring(26));
    }
    static BigInteger quantity(JsonNode value) { return quantityText(text(value)); }
    private static BigInteger quantityText(String text) {
        if (!text.matches("0x[0-9a-fA-F]{1,64}")) throw failure("BESU_RPC_QUANTITY_INVALID");
        return new BigInteger(text.substring(2), 16);
    }
    private static String text(JsonNode value) {
        if (value == null || !value.isTextual()) throw failure("BESU_RPC_VALUE_INVALID");
        return value.textValue();
    }
    private static String hash(JsonNode value) { return SettlementNetwork.hash(text(value)); }
    private static String address(JsonNode value) { return SettlementNetwork.address(text(value)); }
    private static BlockchainRpcException failure(String code) { return new BlockchainRpcException(code); }
    private static void fail(String code) { throw failure(code); }
    private record Transfer(String recipient, BigInteger amount) {}
}
