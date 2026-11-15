package com.cognizant.stablecoins.payment.config;

import com.cognizant.stablecoins.payment.intent.CustomerRoutingPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

/** No default policy: unconfigured customers cannot create intent-bearing payments. */
@Component
@ConfigurationProperties(prefix = "app.routing")
public class RoutingPolicyProperties {
    private Map<UUID, CustomerRoutingPolicy> policies = Map.of();
    public Map<UUID, CustomerRoutingPolicy> getPolicies() { return policies; }
    public void setPolicies(Map<UUID, CustomerRoutingPolicy> policies) {
        this.policies = Map.copyOf(policies);
    }
}
