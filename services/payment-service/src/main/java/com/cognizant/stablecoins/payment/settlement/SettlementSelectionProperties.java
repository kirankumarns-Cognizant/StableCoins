package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.SettlementNetworkRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Empty by default. Configuration cannot enable the unfinished private payment workflow. */
@Component
@ConfigurationProperties(prefix = "app.settlement")
public class SettlementSelectionProperties {
    private List<SettlementNetworkRegistry.Entry> networks = List.of();
    private Map<UUID, Set<String>> allowedNetworks = Map.of();
    public List<SettlementNetworkRegistry.Entry> getNetworks() { return networks; }
    public void setNetworks(List<SettlementNetworkRegistry.Entry> value) { networks = List.copyOf(value); }
    public Map<UUID, Set<String>> getAllowedNetworks() { return allowedNetworks; }
    public void setAllowedNetworks(Map<UUID, Set<String>> value) {
        var copy = new java.util.HashMap<UUID, Set<String>>();
        value.forEach((key, networks) -> copy.put(key, Set.copyOf(networks)));
        allowedNetworks = Map.copyOf(copy);
    }
}
