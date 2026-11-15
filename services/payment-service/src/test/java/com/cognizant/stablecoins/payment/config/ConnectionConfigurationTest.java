package com.cognizant.stablecoins.payment.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Loads actual application imports without starting DB, AWS, or blockchain clients. */
class ConnectionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @ParameterizedTest
    @ValueSource(strings = {"local", "aws-dev", "testnet"})
    void connectionOverridesSurviveProfiles(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile,
                "DB_URL=jdbc:postgresql://configured-db:5433/customer", "DB_USER=customer_role",
                "LOCALSTACK_URL=http://configured-messaging:4566",
                "EVENTBRIDGE_BUS_NAME=customer-bus", "SQS_PAYMENT_CREATED_QUEUE_NAME=customer-created",
                "IDENTITY_ISSUER_URI=https://identity.example.test/pool", "IDENTITY_CLIENT_ID=public-client",
                "BLOCKCHAIN_RPC_URL=https://rpc.example.test", "BLOCKCHAIN_CHAIN_ID=11155111")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var env = context.getEnvironment();
                    assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://configured-db:5433/customer");
                    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("customer_role");
                    assertThat(env.getProperty("connections.aws.endpoint")).isEqualTo("http://configured-messaging:4566");
                    assertThat(env.getProperty("app.eventbridge.bus-name")).isEqualTo("customer-bus");
                    assertThat(env.getProperty("app.sqs.payment-created-queue-name")).isEqualTo("customer-created");
                    assertThat(env.getProperty("connections.identity.issuer-uri")).isEqualTo("https://identity.example.test/pool");
                    assertThat(env.getProperty("public.identity-client-id")).isEqualTo("public-client");
                    assertThat(env.getProperty("blockchain.chain-id")).isEqualTo("11155111");
                    assertThat(env.getProperty("blockchain.rpc-url")).isEqualTo("https://rpc.example.test");
                });
    }

    @Test
    void nestedDatabaseSettingsAndLegacyAliasesAreSupported() {
        runner.withPropertyValues("DB_HOST=aurora.example.test", "DB_PORT=5434", "DB_NAME=tenant",
                "DB_USERNAME=tenant_role", "COGNITO_ISSUER_URI=https://cognito.example.test/pool",
                "BLOCKCHAIN_EXPLORER_URL=https://explorer.example.test")
                .run(context -> {
                    var env = context.getEnvironment();
                    assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://aurora.example.test:5434/tenant");
                    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("tenant_role");
                    assertThat(env.getProperty("connections.identity.issuer-uri")).isEqualTo("https://cognito.example.test/pool");
                    assertThat(env.getProperty("blockchain.explorer-base-url")).isEqualTo("https://explorer.example.test");
                });
    }

    @Test
    void externalFileOverridesPackagedConnections(@TempDir Path directory) throws Exception {
        Path config = directory.resolve("customer.properties");
        Files.writeString(config, "connections.identity.issuer-uri=https://external.example.test/pool\n"
                + "app.sqs.payment-created-queue-name=external-created\n");
        runner.withPropertyValues("CONNECTIONS_CONFIG=" + config.toUri()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("connections.identity.issuer-uri"))
                    .isEqualTo("https://external.example.test/pool");
            assertThat(context.getEnvironment().getProperty("app.sqs.payment-created-queue-name"))
                    .isEqualTo("external-created");
            assertThat(context.getEnvironment().getProperty("blockchain.token-symbol")).isEqualTo("USDC");
        });
    }

    @Test
    void explicitlyMissingExternalFileFailsStartup(@TempDir Path directory) {
        runner.withPropertyValues("CONNECTIONS_CONFIG=" + directory.resolve("missing.properties").toUri())
                .run(context -> assertThat(context).hasFailed());
    }
}
