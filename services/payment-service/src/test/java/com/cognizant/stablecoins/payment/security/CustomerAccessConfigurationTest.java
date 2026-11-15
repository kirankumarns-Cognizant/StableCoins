package com.cognizant.stablecoins.payment.security;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class CustomerAccessConfigurationTest {
    @Test void bindsServerOwnedSubjectMembershipAndRoles() {
        var values=Map.of("app.access.memberships[fictional-subject].customer-id","11111111-1111-1111-1111-111111111111",
            "app.access.memberships[fictional-subject].roles[0]","PAYMENT_OPERATOR","app.access.memberships[fictional-subject].roles[1]","PAYMENT_APPROVER");
        var access=new Binder(new MapConfigurationPropertySource(values)).bind("app.access",Bindable.of(CustomerAccessProperties.class)).get();
        assertThat(access.getMemberships().get("fictional-subject").roles()).containsExactlyInAnyOrder(CustomerAccessProperties.Role.PAYMENT_OPERATOR,CustomerAccessProperties.Role.PAYMENT_APPROVER);
        assertThatThrownBy(()->access.getMemberships().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void missingMembershipNeverProvidesDefaultCustomer() {assertThat(new CustomerAccessProperties().getMemberships()).isEmpty();}
}
