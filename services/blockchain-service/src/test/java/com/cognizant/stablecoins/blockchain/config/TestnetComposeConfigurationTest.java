package com.cognizant.stablecoins.blockchain.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.*;

class TestnetComposeConfigurationTest {
    @SuppressWarnings("unchecked")
    @Test void overlayOptsInOnlyBlockchainAndMountsSecretsWithoutEmbeddingTheirValues() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("docker-compose.yml"))) root = root.getParent();
        Map<String,Object> base = new Yaml().load(Files.readString(root.resolve("docker-compose.yml")));
        Map<String,Object> overlay = new Yaml().load(Files.readString(root.resolve("docker-compose.testnet.yml")));
        var baseEnvironment = (Map<String,Object>) ((Map<?,?>)base.get("x-service-common")).get("environment");
        assertThat(baseEnvironment).containsEntry("BLOCKCHAIN_MODE", "MOCK").containsEntry("TESTNET_ENABLED", "false");
        var services = (Map<String,Object>) overlay.get("services");
        assertThat(services).containsOnlyKeys("blockchain-service");
        var service = (Map<String,Object>) services.get("blockchain-service");
        var env = (Map<String,Object>) service.get("environment");
        assertThat(env).containsEntry("SPRING_PROFILES_ACTIVE", "local,testnet")
                .containsEntry("BLOCKCHAIN_MODE", "REAL_TESTNET").containsEntry("TESTNET_ENABLED", "true")
                .containsEntry("BLOCKCHAIN_CHAIN_ID", "84532").containsEntry("BLOCKCHAIN_WALLET_PRIVATE_KEY", "")
                .containsEntry("BLOCKCHAIN_KEYSTORE_FILE", "/run/secrets/treasury_keystore")
                .containsEntry("BLOCKCHAIN_KEYSTORE_PASSWORD_FILE", "/run/secrets/treasury_password");
        assertThat(env).doesNotContainKeys("BLOCKCHAIN_KEYSTORE_PASSWORD", "TREASURY_PASSWORD");
        assertThat(service.get("secrets").toString()).contains("treasury_keystore", "treasury_password");
        assertThat(service.get("user").toString()).contains("TESTNET_LOCAL_UID:?", "TESTNET_LOCAL_GID:?");
        assertThat(overlay.get("secrets").toString()).contains("TESTNET_KEYSTORE_FILE:?", "TESTNET_PASSWORD_FILE:?");
    }
}
