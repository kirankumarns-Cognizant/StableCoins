package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.payment.api.SettlementPreparationController;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.security.CustomerAccess;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.Authentication;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR;

class SettlementPreparationControllerTest {
    @Test void observationApiIsAbsentByDefaultAndRequiresBothFlags(){
        var runner=new ApplicationContextRunner().withUserConfiguration(SettlementPreparationController.class)
                .withBean(SettlementPreparationService.class,()->mock(SettlementPreparationService.class))
                .withBean(CustomerAccess.class,()->mock(CustomerAccess.class));
        runner.run(c->assertThat(c).doesNotHaveBean(SettlementPreparationController.class));
        runner.withPropertyValues("app.flow.enabled=true").run(c->assertThat(c).doesNotHaveBean(SettlementPreparationController.class));
        runner.withPropertyValues("app.besu.observation-api-enabled=true").run(c->assertThat(c).doesNotHaveBean(SettlementPreparationController.class));
        runner.withPropertyValues("app.flow.enabled=true","app.besu.observation-api-enabled=true")
                .run(c->assertThat(c).hasSingleBean(SettlementPreparationController.class));
    }
    @Test void usesVerifiedOperatorForCreateAndRead(){
        var service=mock(SettlementPreparationService.class);var access=mock(CustomerAccess.class);
        var controller=new SettlementPreparationController(service,access);var auth=mock(Authentication.class);
        var actor=new AuthorizedCustomer(UUID.randomUUID(),"operator");var id=UUID.randomUUID();
        when(access.require(auth,PAYMENT_OPERATOR)).thenReturn(actor);
        controller.prepare("PAY-1",id.toString(),auth);controller.get("PAY-1",id,auth);
        verify(service).prepare("PAY-1",id,actor);verify(service).get("PAY-1",id,actor);
        verify(access,times(2)).require(auth,PAYMENT_OPERATOR);
    }
    @Test void rejectedRoleOrMalformedKeyCannotReachService(){
        var service=mock(SettlementPreparationService.class);var access=mock(CustomerAccess.class);
        var controller=new SettlementPreparationController(service,access);var auth=mock(Authentication.class);
        when(access.require(auth,PAYMENT_OPERATOR)).thenThrow(new IllegalStateException("denied"));
        assertThatThrownBy(()->controller.prepare("PAY-1",UUID.randomUUID().toString(),auth)).hasMessage("denied");
        assertThatThrownBy(()->controller.get("PAY-1",UUID.randomUUID(),auth)).hasMessage("denied");
        assertThatThrownBy(()->controller.prepare("PAY-1","bad-key",auth)).hasMessageContaining("UUID_IDEMPOTENCY_KEY_REQUIRED");
        verifyNoInteractions(service);
    }
}
