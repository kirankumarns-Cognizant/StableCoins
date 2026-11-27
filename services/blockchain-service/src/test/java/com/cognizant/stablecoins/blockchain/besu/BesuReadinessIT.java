package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.blockchain.service.WalletSigningService;
import com.cognizant.stablecoins.flow.SettlementNetwork.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.RawTransaction;
import static org.assertj.core.api.Assertions.*;

/**
 * Explicit real PRIVATE_DEMO RPC acceptance. No mock, password, signing or submission.
 * Run only with -Pbesu-readiness; missing identity/configuration fails rather than skips.
 * This does not establish approvals, journals, reconciliation or public-testnet acceptance.
 */
class BesuReadinessIT {
    @Test void realPrivateRpcHasPinnedIdentityFreshBlocksAndTransferCapacity() throws Exception {
        assertThat(required("BESU_READ_ONLY_ACCEPTANCE")).isEqualTo("PRIVATE_DEMO");
        var chain = new Chain("besu-local", BesuSettings.CHAIN_ID, required("BESU_GENESIS_HASH"), Classification.PRIVATE_DEMO);
        var context = new Context(new Token(chain, required("BESU_TOKEN_CONTRACT"), "DEMOUSD", 6,
                required("BESU_TOKEN_CODE_HASH")), new NativeAsset(chain, "BESU_GAS", 18),
                required("BESU_TREASURY_ADDRESS"), FeeModel.EXECUTION_ONLY, "besu-demo-v1");
        var validators = Arrays.stream(required("BESU_VALIDATOR_ADDRESSES").split(",")).collect(Collectors.toSet());
        String recipient = required("BESU_RECIPIENT_ADDRESS");
        var settings = new BesuSettings(context, validators, Set.of(recipient), BigInteger.valueOf(1000), Duration.ofSeconds(30));
        WalletSigningService noSigner = new WalletSigningService() {
            @Override public String getWalletAddress() { return context.treasury(); }
            @Override public byte[] signTransaction(RawTransaction raw, long chainId) {
                throw new AssertionError("READ_ONLY_ACCEPTANCE_MUST_NEVER_SIGN");
            }
        };
        try (var rpc = new BesuHttpRpc(required("BESU_RPC_URL"))) {
            var adapter = new BesuBlockchainAdapter(settings, rpc, noSigner, Clock.systemUTC());
            adapter.verifyReadiness();
            long before = adapter.getCurrentBlockNumber();
            Thread.sleep(4000); // QBFT is configured for two-second blocks; bounded live progress check.
            assertThat(adapter.getCurrentBlockNumber()).isGreaterThan(before);
            assertThat(adapter.getTokenBalance(context.treasury(), context.token().contract(), 6))
                    .isGreaterThanOrEqualTo(BigDecimal.ONE);
            BigDecimal gas = adapter.estimateTransferGas(context.treasury(), recipient, context.token().contract(), BigDecimal.ONE, 6);
            BigDecimal required = gas.multiply(new BigDecimal("1.2")).setScale(0, RoundingMode.CEILING)
                    .multiply(adapter.getGasPriceWei()).movePointLeft(18);
            assertThat(adapter.getNativeBalance(context.treasury())).isGreaterThanOrEqualTo(required);
            assertThat(adapter.classification()).isEqualTo("PRIVATE_DEMO");
            var now = Instant.now();
            var instruction = new com.cognizant.stablecoins.flow.SettlementBinding.Instruction(
                    UUID.randomUUID(), 1, UUID.randomUUID(), "PAY-BESU-READ-ONLY", "CORR-BESU-READ-ONLY",
                    context, recipient, BigDecimal.ONE, BigDecimal.ONE,
                    com.cognizant.stablecoins.flow.SettlementBinding.Outcome.TOKEN_WALLET,
                    BigDecimal.ZERO, now.plusSeconds(60));
            try (var runtime = new BesuRuntime(adapter, rpc, Clock.systemUTC())) {
                var observation = runtime.observe(instruction);
                observation.requireFor(instruction, Instant.now());
                assertThat(observation.totalsCoverEstimate()).isTrue();
                assertThatThrownBy(() -> runtime.forNewWork("besu-local")).hasMessage("NETWORK_DISABLED");
            }

        }
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("MISSING_" + name);
        return value;
    }
}
