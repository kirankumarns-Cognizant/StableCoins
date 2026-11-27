package com.cognizant.stablecoins.visa.liquidity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisaWalletConfigurationTest {
    final ApplicationContextRunner runner=new ApplicationContextRunner()
            .withUserConfiguration(VisaWalletConfiguration.class,Dependencies.class)
            .withPropertyValues("visa.simulator.allowed-provider-ids=SIMULATED-PROVIDER-A",
                    "visa.simulator.wallet.scenario=SUCCEEDED");
    @org.springframework.boot.test.context.TestConfiguration static class Dependencies {
        @Bean WalletPayoutStore store(){return mock(WalletPayoutStore.class);}
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
    }
    @Test void walletCapabilityIsAbsentByDefault(){
        runner.run(c->assertThat(c).doesNotHaveBean(VisaWalletSimulator.class));
    }
    @Test void enabledButMissingPinnedTokenCannotStart(){
        runner.withPropertyValues("visa.simulator.wallet.enabled=true").run(c->{
            assertThat(c).hasFailed();
            assertThat(c.getStartupFailure()).hasRootCauseMessage("INVALID_WALLET_SIMULATOR_CONFIGURATION");
        });
    }
    @Test void emptyTokenListCannotEnableAnUnrestrictedSimulator(){
        runner.withPropertyValues("visa.simulator.wallet.enabled=true","visa.simulator.wallet.tokens-json=[]").run(c->assertThat(c).hasFailed());
    }
    @Test void explicitPinnedTokenCreatesIsolatedProviderCapability() throws Exception {
        String json=new ObjectMapper().writeValueAsString(Set.of(VisaWalletSimulatorTest.TOKEN));
        runner.withPropertyValues("visa.simulator.wallet.enabled=true","visa.simulator.wallet.tokens-json="+json)
                .run(c->assertThat(c).hasSingleBean(VisaWalletSimulator.class));
    }
}