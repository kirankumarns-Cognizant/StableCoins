package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.blockchain.service.*;
import com.cognizant.stablecoins.flow.SettlementNetwork.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;
import static org.assertj.core.api.Assertions.*;

class BesuBlockchainAdapterTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Credentials WALLET = Credentials.create("2"); // disposable fixture, never an owner wallet
    static final String RECIPIENT = "0x" + "3".repeat(40);
    static final String CONTRACT = "0x" + "4".repeat(40);
    static final String GENESIS = "0x" + "a".repeat(64), BLOCK = "0x" + "b".repeat(64);
    static final String TX = "0x" + "c".repeat(64), CODE = "0x60006000";
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneOffset.UTC);
    static final String DATA = "0xa9059cbb" + "0".repeat(24) + RECIPIENT.substring(2)
            + Numeric.toHexStringNoPrefixZeroPadded(BigInteger.valueOf(1_000_000), 64);
    static Context context() {
        var chain = new Chain("besu-local", BesuSettings.CHAIN_ID, GENESIS, Classification.PRIVATE_DEMO);
        return new Context(new Token(chain, CONTRACT, "DEMOUSD", 6, Hash.sha3(CODE)),
                new NativeAsset(chain, "BESU_GAS", 18), WALLET.getAddress(), FeeModel.EXECUTION_ONLY, "besu-demo-v1");
    }
    static BesuSettings settings() {
        return new BesuSettings(context(), Set.of("0x"+"5".repeat(40), "0x"+"6".repeat(40),
                "0x"+"7".repeat(40), "0x"+"8".repeat(40)), Set.of(RECIPIENT),
                BigInteger.valueOf(1000), Duration.ofSeconds(30));
    }
    static byte[] signed(long chain, String contract, String data, BigInteger value) {
        return TransactionEncoder.signMessage(RawTransaction.createTransaction(BigInteger.ZERO,
                BigInteger.valueOf(1000), BigInteger.valueOf(75000), contract, value, data), chain, WALLET);
    }
    static final class Fixture implements BesuRpc, WalletSigningService {
        final Map<String, JsonNode> overrides = new HashMap<>();
        final Map<String, Integer> calls = new HashMap<>();
        int signatures;
        boolean unknownSubmission;
        ObjectNode receipt;
        ObjectNode transaction;
        BesuBlockchainAdapter adapter() { return new BesuBlockchainAdapter(settings(), this, this, CLOCK); }
        @Override public String getWalletAddress() { return WALLET.getAddress(); }
        @Override public byte[] signTransaction(RawTransaction raw, long chain) {
            signatures++;
            return TransactionEncoder.signMessage(raw, chain, WALLET);
        }
        @Override public JsonNode call(String method, List<?> params) {
            calls.merge(method, 1, Integer::sum);
            if (overrides.containsKey(method)) return overrides.get(method);
            return switch (method) {
                case "eth_chainId" -> value(Numeric.encodeQuantity(BigInteger.valueOf(BesuSettings.CHAIN_ID)));
                case "eth_getBlockByNumber" -> JSON.valueToTree(Map.of("hash", params.getFirst().equals("0x0") ? GENESIS : BLOCK,
                        "number", "0x64", "timestamp", Numeric.encodeQuantity(BigInteger.valueOf(CLOCK.instant().getEpochSecond()))));
                case "qbft_getValidatorsByBlockNumber" -> JSON.valueToTree(settings().validators());
                case "eth_getCode" -> value(CODE);
                case "eth_call" -> {
                    String data = (String) ((Map<?, ?>) params.getFirst()).get("data");
                    yield value(switch (data) {
                        case "0x313ce567" -> "0x6";
                        case "0x18160ddd" -> "0x" + BesuSettings.SUPPLY.toString(16);
                        case "0x95d89b41" -> Numeric.prependHexPrefix(FunctionEncoder.encodeConstructor(List.of(new Utf8String("DEMOUSD"))));
                        default -> "0x" + BesuSettings.SUPPLY.toString(16);
                    });
                }
                case "eth_gasPrice" -> value("0x3e8");
                case "eth_estimateGas" -> value("0xf424"); // 62500
                case "eth_getBalance" -> value("0xde0b6b3a7640000");
                case "eth_getTransactionCount" -> value("0x0");
                case "eth_getTransactionReceipt" -> receipt == null ? NullNode.instance : receipt;
                case "eth_getTransactionByHash" -> transaction;
                case "eth_sendRawTransaction" -> {
                    if (unknownSubmission) throw new BlockchainRpcException("BESU_RPC_UNAVAILABLE_OR_REJECTED");
                    yield value(Hash.sha3((String) params.getFirst()));
                }
                default -> throw new AssertionError("Unexpected method " + method);
            };
        }
        void mined() {
            transaction = JSON.valueToTree(Map.of("hash", TX, "from", WALLET.getAddress(), "to", CONTRACT,
                    "chainId", Numeric.encodeQuantity(BigInteger.valueOf(BesuSettings.CHAIN_ID)), "value", "0x0",
                    "blockHash", BLOCK, "blockNumber", "0x64", "input", DATA));
            var log = JSON.valueToTree(Map.of("address", CONTRACT, "topics", List.of(Hash.sha3String("Transfer(address,address,uint256)"),
                    "0x"+"0".repeat(24)+WALLET.getAddress().substring(2), "0x"+"0".repeat(24)+RECIPIENT.substring(2)),
                    "data", "0x" + Numeric.toHexStringNoPrefixZeroPadded(BigInteger.valueOf(1_000_000), 64),
                    "transactionHash", TX, "blockHash", BLOCK, "blockNumber", "0x64", "removed", false));
            receipt = JSON.valueToTree(Map.of("transactionHash", TX, "from", WALLET.getAddress(), "to", CONTRACT,
                    "status", "0x1", "blockNumber", "0x64", "blockHash", BLOCK,
                    "gasUsed", "0xf424", "effectiveGasPrice", "0x3e8", "logs", List.of(log)));
        }
    }
    static TextNode value(String text) { return TextNode.valueOf(text); }

    @Test void observesRealPrivateEvidenceWithoutSigningOrBroadcasting() {
        var f = new Fixture(); var adapter = f.adapter();
        adapter.verifyReadiness();
        assertThat(adapter.getChainId()).isEqualTo(BesuSettings.CHAIN_ID);
        assertThat(adapter.classification()).isEqualTo("PRIVATE_DEMO");
        assertThat(adapter.getEvidenceMode()).isEqualTo("LIVE");
        assertThat(adapter.getTokenBalance(WALLET.getAddress(), CONTRACT, 6)).isEqualByComparingTo("1000000");
        assertThat(adapter.getNativeBalance(WALLET.getAddress())).isEqualByComparingTo("1");
        assertThat(adapter.getPendingNonce(WALLET.getAddress())).isZero();
        assertThat(f.signatures).isZero();
        assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
    }
    @ParameterizedTest @ValueSource(strings={"chain", "genesis", "code", "validators", "halt", "future", "metadata"})
    void identityAndFreshnessFailuresPreventSigning(String failure) {
        var f = new Fixture();
        switch (failure) {
            case "chain" -> f.overrides.put("eth_chainId", value("0x14a34"));
            case "genesis" -> f.overrides.put("eth_getBlockByNumber", JSON.valueToTree(Map.of("hash", BLOCK)));
            case "code" -> f.overrides.put("eth_getCode", value("0x6001"));
            case "validators" -> f.overrides.put("qbft_getValidatorsByBlockNumber", JSON.createArrayNode());
            case "metadata" -> f.overrides.put("eth_call", value("0x12"));
            default -> {
                long delta = failure.equals("future") ? 300 : -300;
                f.overrides.put("eth_getBlockByNumber", JSON.valueToTree(Map.of("hash", GENESIS,
                        "number", "0x64", "timestamp", Numeric.encodeQuantity(BigInteger.valueOf(CLOCK.instant().getEpochSecond()+delta)))));
            }
        }
        assertThatThrownBy(() -> f.adapter().signRawTransaction(0, RECIPIENT, CONTRACT, BigDecimal.ONE, 6,
                new BigDecimal("75000"), new BigDecimal("1000"))).isInstanceOf(RuntimeException.class);
        assertThat(f.signatures).isZero();
        assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
    }
    @Test void signsExactChainTokenRecipientAmountAndGasWithoutSubmitting() throws Exception {
        var f = new Fixture();
        byte[] bytes = f.adapter().signRawTransaction(7, RECIPIENT, CONTRACT, new BigDecimal("1.000000"), 6,
                new BigDecimal("75000"), new BigDecimal("1000"));
        var signed = (SignedRawTransaction) TransactionDecoder.decode(Numeric.toHexString(bytes));
        assertThat(signed.getChainId()).isEqualTo(BesuSettings.CHAIN_ID);
        assertThat(signed.getFrom()).isEqualToIgnoringCase(WALLET.getAddress());
        assertThat(signed.getNonce()).isEqualTo(BigInteger.valueOf(7));
        assertThat(signed.getTo()).isEqualToIgnoringCase(CONTRACT);
        assertThat(Numeric.prependHexPrefix(signed.getData())).isEqualToIgnoringCase(DATA);
        assertThat(signed.getValue()).isZero();
        assertThat(f.signatures).isOne();
        assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
    }
    @Test void rejectsUnacceptedRecipientTokenAndPrecisionBeforeNetworkOrSigning() {
        var f = new Fixture(); var adapter = f.adapter();
        assertThatThrownBy(() -> adapter.estimateTransferGas(WALLET.getAddress(), "0x"+"9".repeat(40), CONTRACT, BigDecimal.ONE, 6))
                .hasMessage("BESU_RECIPIENT_NOT_ACCEPTED");
        assertThatThrownBy(() -> adapter.getTokenBalance(WALLET.getAddress(), RECIPIENT, 6)).hasMessage("BESU_TOKEN_CONTEXT_MISMATCH");
        assertThatThrownBy(() -> adapter.estimateTransferGas(WALLET.getAddress(), RECIPIENT, CONTRACT, new BigDecimal("0.0000001"), 6))
                .hasMessage("BESU_TOKEN_AMOUNT_INVALID");
        assertThat(f.calls).isEmpty(); assertThat(f.signatures).isZero();
    }
    @Test void insufficientGasOrLimitPreventsSigning() {
        var f = new Fixture(); f.overrides.put("eth_getBalance", value("0x0"));
        assertThatThrownBy(() -> f.adapter().signRawTransaction(0, RECIPIENT, CONTRACT, BigDecimal.ONE, 6,
                new BigDecimal("75000"), new BigDecimal("1000"))).hasMessage("BESU_GAS_BALANCE_INSUFFICIENT");
        assertThatThrownBy(() -> f.adapter().signRawTransaction(0, RECIPIENT, CONTRACT, BigDecimal.ONE, 6,
                new BigDecimal("21000"), new BigDecimal("1000"))).hasMessage("BESU_GAS_LIMIT_INSUFFICIENT");
        assertThat(f.signatures).isZero();
    }
    @Test void broadcastsOnceAndNeverRetriesUnknownOutcome() {
        var f = new Fixture();
        byte[] bytes = signed(BesuSettings.CHAIN_ID, CONTRACT, DATA, BigInteger.ZERO);
        assertThat(f.adapter().submitRawTransaction(bytes)).isEqualTo(Hash.sha3(Numeric.toHexString(bytes)));
        assertThat(f.calls.get("eth_sendRawTransaction")).isOne();
        f.calls.clear(); f.unknownSubmission = true;
        assertThatThrownBy(() -> f.adapter().submitRawTransaction(bytes)).hasMessage("BESU_RPC_UNAVAILABLE_OR_REJECTED");
        assertThat(f.calls.get("eth_sendRawTransaction")).isOne();
        assertThat(f.signatures).isZero();
    }
    @Test void rejectsForeignChainNativeTransferWrongTokenAndUnsignedBytesBeforeRpc() {
        var f = new Fixture();
        for (byte[] bytes : List.of(signed(84532, CONTRACT, DATA, BigInteger.ZERO),
                signed(BesuSettings.CHAIN_ID, RECIPIENT, DATA, BigInteger.ZERO),
                signed(BesuSettings.CHAIN_ID, CONTRACT, DATA, BigInteger.ONE), new byte[]{1, 2})) {
            assertThatThrownBy(() -> f.adapter().submitRawTransaction(bytes)).hasMessage("BESU_SIGNED_TRANSACTION_CONTEXT_MISMATCH");
        }
        assertThat(f.calls).isEmpty();
    }
    @Test void canonicalReceiptVerifiesExactTransferAndExecutionOnlyFee() {
        var f = new Fixture(); f.mined();
        var receipt = f.adapter().getReceipt(TX).orElseThrow();
        assertThat(receipt.isSuccess()).isTrue();
        assertThat(receipt.getBlockNumber()).isEqualTo(100);
        assertThat(receipt.isFeeComponentsComplete()).isTrue();
        assertThat(receipt.getAdditionalFeeWei()).isZero();
        assertThat(receipt.getTotalFeeWei()).isEqualTo(BigInteger.valueOf(62_500_000));
        assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
        assertThat(f.signatures).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"amount", "recipient", "removed", "duplicate", "missing", "block", "chain", "fee"})
    void refusesMismatchedReceiptEvidence(String failure) {
        var f = new Fixture(); f.mined();
        var logs = (ArrayNode) f.receipt.get("logs"); var log = (ObjectNode) logs.get(0);
        switch (failure) {
            case "amount" -> log.put("data", "0x2");
            case "recipient" -> ((ArrayNode) log.get("topics")).set(2, value("0x"+"0".repeat(24)+"9".repeat(40)));
            case "removed" -> log.put("removed", true);
            case "duplicate" -> logs.add(log.deepCopy());
            case "missing" -> logs.removeAll();
            case "block" -> f.receipt.put("blockHash", GENESIS);
            case "chain" -> f.transaction.put("chainId", "0x14a34");
            case "fee" -> f.receipt.put("l1Fee", "0x1");
        }
        assertThatThrownBy(() -> f.adapter().getReceipt(TX)).isInstanceOf(RuntimeException.class);
    }
    @Test void unknownReceiptDoesNotBecomeFailureOrResubmissionAndZeroFeeRemainsComplete() {
        var f = new Fixture();
        assertThat(f.adapter().getReceipt(TX)).isEmpty();
        f.mined(); f.receipt.put("effectiveGasPrice", "0x0");
        assertThat(f.adapter().getReceipt(TX).orElseThrow().getTotalFeeWei()).isZero();
        f.receipt.remove("effectiveGasPrice");
        assertThat(f.adapter().getReceipt(TX).orElseThrow().isFeeComponentsComplete()).isFalse();
        assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
    }
    @Test void pinnedRuntimeRejectsChangedContextAndKeepsPaymentCreationDisabled() {
        var f = new Fixture();
        try (var runtime = new BesuRuntime(f.adapter(), new BesuHttpRpc("http://127.0.0.1:1"), CLOCK)) {
            assertThatThrownBy(() -> runtime.forNewWork("besu-local")).hasMessage("NETWORK_DISABLED");
            assertThat(runtime.forPinnedExecution(context()).context()).isEqualTo(context());
            var changed = new Context(context().token(), context().nativeAsset(), context().treasury(),
                    context().feeModel(), "changed-policy");
            assertThatThrownBy(() -> runtime.forPinnedExecution(changed)).hasMessage("PINNED_NETWORK_CONTEXT_CHANGED");
            var observation = runtime.observe(RECIPIENT, BigDecimal.ONE);
            assertThat(observation.balancesCoverEstimate()).isTrue();
            assertThat(observation.executionGate()).isEqualTo("PAYMENT_INTEGRATION_NOT_ENABLED");
            assertThat(observation.expiresAt()).isAfter(observation.observedAt());
        }
        assertThat(f.signatures).isZero();
    }
    @Test void recoveryMatchesExactSignedTransactionWithoutBroadcasting() {
        var f=new Fixture();
        byte[] bytes=signed(BesuSettings.CHAIN_ID,CONTRACT,DATA,BigInteger.ZERO);
        f.transaction=JSON.valueToTree(Map.of("hash",Hash.sha3(Numeric.toHexString(bytes)),"from",WALLET.getAddress(),
                "to",CONTRACT,"chainId",Numeric.encodeQuantity(BigInteger.valueOf(BesuSettings.CHAIN_ID)),
                "nonce","0x0","value","0x0","gas","0x124f8","gasPrice","0x3e8","input",DATA));
        assertThat(f.adapter().observesTransaction(bytes)).isTrue();
        assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
        f.transaction.put("nonce","0x1");
        assertThatThrownBy(()->f.adapter().observesTransaction(bytes)).hasMessage("BESU_RECOVERY_TRANSACTION_MISMATCH");
    }
    @Test void absentRecoveryTransactionDoesNotAuthorizeResubmission() {
        var f=new Fixture();
        assertThat(f.adapter().observesTransaction(signed(BesuSettings.CHAIN_ID,CONTRACT,DATA,BigInteger.ZERO))).isFalse();
        assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
    }
    static BesuSignedTransfer savedTransfer(Fixture f) {
        var now=CLOCK.instant();
        var i=new com.cognizant.stablecoins.flow.SettlementBinding.Instruction(UUID.randomUUID(),1,UUID.randomUUID(),
                "PAY-EXACT-RECEIPT","CORR-EXACT",context(),RECIPIENT,BigDecimal.ONE,BigDecimal.ONE,
                com.cognizant.stablecoins.flow.SettlementBinding.Outcome.TOKEN_WALLET,BigDecimal.TEN,now.plusSeconds(600));
        var review=new com.cognizant.stablecoins.flow.SettlementBinding.Review(UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),1,
                "PROVIDER",i,BigDecimal.ONE,new BigDecimal("0.1"),now,now.plusSeconds(60));
        var e=new com.cognizant.stablecoins.flow.SettlementBinding.Execution(UUID.randomUUID(),review);
        var t=new BesuSignedTransfer(e,0,new BigDecimal("75000"),new BigDecimal("1000"),
                signed(BesuSettings.CHAIN_ID,CONTRACT,DATA,BigInteger.ZERO));
        f.mined(); f.receipt.put("transactionHash",t.hash); f.transaction.put("hash",t.hash);
        f.transaction.put("nonce","0x0").put("gas","0x124f8").put("gasPrice","0x3e8");
        ((ObjectNode)f.receipt.path("logs").get(0)).put("transactionHash",t.hash);
        return t;
    }
    @Test void finalReceiptIsBoundToSavedSignedTransferAndCanonicalBlock() {
        var f=new Fixture(); var t=savedTransfer(f);
        var r=f.adapter().finalReceipt(t).orElseThrow();
        assertThat(r.status()).isEqualTo("CONFIRMED"); assertThat(r.blockHash()).isEqualTo(BLOCK);
        assertThat(r.totalFeeWei()).isEqualTo(BigInteger.valueOf(62500000));
        r.requireFor(t); assertThat(f.signatures).isZero(); assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
    }
    @ParameterizedTest @ValueSource(strings={"nonce","gas","gasPrice","input","missingPrice","overGas","effectivePrice","revertLogs"})
    void finalReceiptRejectsSignedTransactionOrFeeMismatch(String problem) {
        var f=new Fixture(); var t=savedTransfer(f);
        switch(problem) {
            case "nonce" -> f.transaction.put("nonce","0x1");
            case "gas" -> f.transaction.put("gas","0x124f9");
            case "gasPrice" -> f.transaction.put("gasPrice","0x3e9");
            case "input" -> f.transaction.put("input",DATA.substring(0,DATA.length()-1)+"2");
            case "missingPrice" -> f.receipt.remove("effectiveGasPrice");
            case "effectivePrice" -> f.receipt.put("effectiveGasPrice","0x3e9");
            case "overGas" -> f.receipt.put("gasUsed","0x124f9");
            case "revertLogs" -> f.receipt.put("status","0x0");
        }
        assertThatThrownBy(()->f.adapter().finalReceipt(t)).isInstanceOf(RuntimeException.class);
    }
    @Test void conclusiveRevertHasNoTransferLogsButStillHasExactGasCost() {
        var f=new Fixture(); var t=savedTransfer(f);
        f.receipt.put("status","0x0").set("logs",JSON.createArrayNode());
        var r=f.adapter().finalReceipt(t).orElseThrow();
        assertThat(r.status()).isEqualTo("REVERTED"); assertThat(r.totalFeeWei()).isEqualTo(BigInteger.valueOf(62500000));
    }
    @Test void blockIdentityChangeDuringReceiptReadCannotResolveCapacity() {
        var f=new Fixture(); var t=savedTransfer(f); var reads=new java.util.concurrent.atomic.AtomicInteger();
        BesuRpc changing=(method,params)->{
            if (method.equals("eth_getBlockByNumber") && params.getFirst().equals("0x64") && reads.incrementAndGet()==2)
                return JSON.valueToTree(Map.of("hash",GENESIS));
            return f.call(method,params);
        };
        var adapter=new BesuBlockchainAdapter(settings(),changing,f,CLOCK);
        assertThatThrownBy(()->adapter.finalReceipt(t)).hasMessage("BESU_RECEIPT_NOT_CANONICAL");
    }
}
