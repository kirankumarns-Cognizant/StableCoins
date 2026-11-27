package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.blockchain.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BesuConfiguration.class);
    @Test void disabledByDefaultAndDoesNotCreateCompetingPublicBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(BesuRuntime.class);
            assertThat(context).doesNotHaveBean(BlockchainAdapter.class);
            assertThat(context).doesNotHaveBean(WalletSigningService.class);
        });
        runner.withPropertyValues("app.besu.enabled=false").run(context -> assertThat(context).hasNotFailed());
    }
    @Test void rejectsMissingLocalAndAwsProfileBeforeReadingAnySecretOrRpc() {
        var signer = mock(WalletSigningService.class);
        var config = new BesuConfiguration(); var env = new MockEnvironment();
        assertThatThrownBy(() -> config.besuRuntime(env, signer)).hasMessage("BESU_PRIVATE_DEMO_LOCAL_ONLY");
        env.setActiveProfiles("local", "aws-dev");
        assertThatThrownBy(() -> config.besuRuntime(env, signer)).hasMessage("BESU_PRIVATE_DEMO_LOCAL_ONLY");
        verifyNoInteractions(signer);
    }
    @Test void explicitPrivateEnablementRejectsIncompleteIdentityWithoutDefaultingToPublic() {
        runner.withPropertyValues("app.besu.enabled=true", "spring.profiles.active=local")
                .withBean(WalletSigningService.class, () -> mock(WalletSigningService.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("BESU_CONFIGURATION_MISSING_BESU_GENESIS_HASH");
                });
    }
}
