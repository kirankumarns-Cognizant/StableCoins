package com.cognizant.stablecoins.payment.config;

import com.cognizant.stablecoins.payment.intent.BeneficiaryOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.*;
import static com.cognizant.stablecoins.payment.support.IntentFixtures.*;
import static org.assertj.core.api.Assertions.*;

class RoutingPolicyPropertiesTest {
    Map<String,Object> source() {
        String root="app.routing.policies["+CUSTOMER+"]";
        return new HashMap<>(Map.of(root+".version","configured-v1",root+".allowed-outcomes[0]","USDC_WALLET",
            root+".allowed-provider-ids[0]","SIMULATED-PROVIDER-A",root+".maximum-fee-usd","30.00",root+".required-approvals","2"));
    }
    @Test void bindsVersionedCustomerPolicyFromServerConfiguration() {
        RoutingPolicyProperties p=new Binder(new MapConfigurationPropertySource(source()))
            .bind("app.routing",Bindable.of(RoutingPolicyProperties.class)).get();
        assertThat(p.getPolicies().get(CUSTOMER).version()).isEqualTo("configured-v1");
        assertThat(p.getPolicies().get(CUSTOMER).allowedOutcomes()).containsExactly(BeneficiaryOutcome.USDC_WALLET);
        assertThat(p.getPolicies().get(CUSTOMER).requiredApprovals()).isEqualTo(2);
    }
    @Test void invalidApprovalConfigurationFailsBinding() {
        var values=source();values.put("app.routing.policies["+CUSTOMER+"].required-approvals","0");
        assertThatThrownBy(()->new Binder(new MapConfigurationPropertySource(values))
            .bind("app.routing",Bindable.of(RoutingPolicyProperties.class))).isInstanceOf(BindException.class);
    }
    @Test void noImplicitCustomerPolicyOrMutableRegistry() {
        assertThat(new RoutingPolicyProperties().getPolicies()).isEmpty();
        assertThatThrownBy(()->policies().getPolicies().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
