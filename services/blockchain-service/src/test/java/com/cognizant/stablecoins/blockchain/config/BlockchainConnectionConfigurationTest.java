package com.cognizant.stablecoins.blockchain.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockchainConnectionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void localProfileRemainsMockEvenWithRealFlags() {
        runner.withPropertyValues("spring.profiles.active=local", "BLOCKCHAIN_MODE=REAL_TESTNET", "TESTNET_ENABLED=true")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("blockchain.mode")).isEqualTo("MOCK");
                    assertThat(context.getEnvironment().getProperty("testnet.enabled")).isEqualTo("false");
                    assertThat(context.getEnvironment().getProperty("spring.cloud.aws.credentials.access-key")).isEqualTo("test");
                });
    }

    @Test
    void explicitTestnetProfileAfterLocalRestoresConfiguredFlagsAndNetwork() {
        runner.withPropertyValues("spring.profiles.active=local,testnet", "BLOCKCHAIN_MODE=REAL_TESTNET",
                "TESTNET_ENABLED=true", "BLOCKCHAIN_NETWORK=ethereum-sepolia", "BLOCKCHAIN_CHAIN_ID=11155111")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("blockchain.mode")).isEqualTo("REAL_TESTNET");
                    assertThat(context.getEnvironment().getProperty("testnet.enabled")).isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty("blockchain.network")).isEqualTo("ethereum-sepolia");
                    assertThat(context.getEnvironment().getProperty("blockchain.chain-id")).isEqualTo("11155111");
                });
    }

    @Test
    void realModeRejectsMissingRpcBeforeConstructingAClient() {
        var props = new BlockchainProperties();
        props.setMode(com.cognizant.stablecoins.blockchain.enums.BlockchainMode.REAL_TESTNET);
        var testnet = new TestnetProperties();
        testnet.setEnabled(true);
        assertThatThrownBy(() -> new BlockchainConfig().web3j(props, testnet))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BLOCKCHAIN_RPC_URL");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rpcTimeoutsMustBePositive(int timeout) {
        var props = new BlockchainProperties();
        assertThatThrownBy(() -> props.setRpcConnectTimeoutMs(timeout)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> props.setRpcReadTimeoutMs(timeout)).isInstanceOf(IllegalArgumentException.class);
    }
}
