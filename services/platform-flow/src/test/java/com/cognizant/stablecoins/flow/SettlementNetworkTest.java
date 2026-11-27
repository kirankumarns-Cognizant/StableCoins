package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.flow.SettlementNetwork.*;

class SettlementNetworkTest {
    static String hash(String digit) { return "0x" + digit.repeat(64); }
    static String addr(String digit) { return "0x" + digit.repeat(40); }
    static Context context(String genesis) {
        var chain = new Chain("besu-local", 20261125, hash(genesis), Classification.PRIVATE_DEMO);
        return new Context(new Token(chain, addr("a"), "DEMOUSD", 6, hash("b")),
                new NativeAsset(chain, "BESU_GAS", 18), addr("c"), FeeModel.EXECUTION_ONLY, "besu-demo-v1");
    }
    @Test void roundTripRetainsAllApprovalBindings() throws Exception {
        var mapper = new ObjectMapper();
        var original = context("a");
        assertThat(mapper.readValue(mapper.writeValueAsString(original), Context.class)).isEqualTo(original);
    }
    @Test void privateChainResetChangesNonceAndLiquidityScopes() {
        assertThat(context("a").nonceScope()).isNotEqualTo(context("b").nonceScope());
        assertThat(context("a").liquidityScope()).isNotEqualTo(context("b").liquidityScope());
    }
    @Test void cannotCallPrivateTokenCircleUsdc() {
        var chain = context("a").token().chain();
        assertThatThrownBy(() -> new Token(chain, addr("a"), "USDC", 6, hash("a")))
                .hasMessage("PRIVATE_DEMO_IS_NOT_CIRCLE_USDC");
    }
    @Test void rejectsWrongGasChainAndPublicChainIdMasqueradingAsPrivate() {
        assertThatThrownBy(() -> new Chain("besu-local", 84532, hash("a"), Classification.PRIVATE_DEMO))
                .hasMessage("PRIVATE_NETWORK_USES_PUBLIC_CHAIN_ID");
        var c = context("a");
        assertThatThrownBy(() -> new Context(c.token(), context("b").nativeAsset(), c.treasury(), c.feeModel(), c.policyVersion()))
                .hasMessage("TOKEN_AND_GAS_CHAIN_MISMATCH");
    }
    @Test void baseStillRequiresL1FeeEvidence() {
        var chain = new Chain("base-sepolia", 84532, hash("a"), Classification.PUBLIC_TESTNET);
        assertThatThrownBy(() -> new Context(new Token(chain, addr("a"), "USDC", 6, hash("a")),
                new NativeAsset(chain, "ETH", 18), addr("b"), FeeModel.EXECUTION_ONLY, "v1"))
                .hasMessage("BASE_L1_FEE_EVIDENCE_REQUIRED");
    }
    @Test void missingOrZeroIdentityFailsClosed() {
        assertThatThrownBy(() -> new Chain("besu-local", 20261125, null, Classification.PRIVATE_DEMO))
                .hasMessage("INVALID_NETWORK_HASH");
        assertThatThrownBy(() -> new Token(context("a").token().chain(), addr("0"), "DEMOUSD", 6, hash("a")))
                .hasMessage("INVALID_NETWORK_ADDRESS");
    }
    @Test void registryNeverFallsBackForUnknownOrDisabledNetwork() {
        var registry = new SettlementNetworkRegistry(List.of(new SettlementNetworkRegistry.Entry(context("a"), false)));
        assertThatThrownBy(() -> registry.forNewWork("base-sepolia")).hasMessage("NETWORK_NOT_CONFIGURED");
        assertThatThrownBy(() -> registry.forNewWork("besu-local")).hasMessage("NETWORK_DISABLED");
        assertThat(registry.forPinnedExecution(context("a"))).isEqualTo(context("a"));
    }
    @Test void registryRejectsChangedGenesisCodeRecipientSignerOrPolicyBindings() {
        var registry = new SettlementNetworkRegistry(List.of(new SettlementNetworkRegistry.Entry(context("a"), true)));
        assertThatThrownBy(() -> registry.forPinnedExecution(context("b"))).hasMessage("PINNED_NETWORK_CONTEXT_CHANGED");
        var c = context("a");
        var changed = new Context(c.token(), c.nativeAsset(), addr("d"), c.feeModel(), c.policyVersion());
        assertThatThrownBy(() -> registry.forPinnedExecution(changed)).hasMessage("PINNED_NETWORK_CONTEXT_CHANGED");
        var token = new Token(c.token().chain(), c.token().contract(), "DEMOUSD", 6, hash("d"));
        assertThatThrownBy(() -> registry.forPinnedExecution(new Context(token, c.nativeAsset(), c.treasury(), c.feeModel(), c.policyVersion())))
                .hasMessage("PINNED_NETWORK_CONTEXT_CHANGED");
    }
    @Test void registryRejectsDuplicateIds() {
        assertThatThrownBy(() -> new SettlementNetworkRegistry(List.of(
                new SettlementNetworkRegistry.Entry(context("a"), true),
                new SettlementNetworkRegistry.Entry(context("b"), true)))).hasMessage("DUPLICATE_NETWORK_ID");
    }
}
