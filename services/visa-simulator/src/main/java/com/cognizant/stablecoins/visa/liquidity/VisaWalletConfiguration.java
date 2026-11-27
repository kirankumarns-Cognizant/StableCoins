package com.cognizant.stablecoins.visa.liquidity;

import com.cognizant.stablecoins.flow.SettlementNetwork;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(name="visa.simulator.wallet.enabled", havingValue="true")
public class VisaWalletConfiguration {
    @Bean VisaWalletSimulator visaWalletSimulator(WalletPayoutStore store, ObjectMapper mapper, Environment env) {
        try {
            Set<SettlementNetwork.Token> tokens = mapper.readValue(
                    env.getRequiredProperty("visa.simulator.wallet.tokens-json"), new TypeReference<>() {});
            if (tokens.isEmpty()) throw new IllegalArgumentException();
            return new VisaWalletSimulator(store,
                    Set.of(env.getRequiredProperty("visa.simulator.allowed-provider-ids").split(",")), tokens,
                    WalletPayouts.Scenario.valueOf(env.getRequiredProperty("visa.simulator.wallet.scenario")));
        } catch (Exception error) {
            // Configuration can contain deployment metadata; never echo its raw value.
            throw new IllegalArgumentException("INVALID_WALLET_SIMULATOR_CONFIGURATION");
        }
    }
}