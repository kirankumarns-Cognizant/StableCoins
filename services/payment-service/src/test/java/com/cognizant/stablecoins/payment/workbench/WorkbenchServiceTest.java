package com.cognizant.stablecoins.payment.workbench;
import com.cognizant.stablecoins.payment.service.PaymentService;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WorkbenchServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final PaymentService payments=mock(PaymentService.class);
 final CustomerAccessProperties access=new CustomerAccessProperties();
 final WorkbenchService views=new WorkbenchService(jdbc,new ObjectMapper(),payments,access,new RoutingPolicyProperties());
 @Test void foreignPaymentIsDeniedBeforeAnyCrossOwnerRead(){var actor=new AuthorizedCustomer(UUID.randomUUID(),"operator");when(payments.getPayment("PAY-FOREIGN",actor)).thenThrow(new IllegalArgumentException("PAYMENT_NOT_FOUND"));assertThrows(IllegalArgumentException.class,()->views.evidence("PAY-FOREIGN",actor));verifyNoInteractions(jdbc);}
 @Test void unconfiguredMembershipCannotReadCatalog(){assertThrows(IllegalStateException.class,()->views.catalog(new AuthorizedCustomer(UUID.randomUUID(),"unmapped")));verifyNoInteractions(jdbc);}
 @Test void membershipCannotBeReboundToAnotherCustomer(){var configured=UUID.randomUUID();access.setMemberships(Map.of("subject",new com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Membership(configured,Set.of(com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR))));assertThrows(IllegalStateException.class,()->views.catalog(new AuthorizedCustomer(UUID.randomUUID(),"subject")));verifyNoInteractions(jdbc);}
 @Test void exactProjectionPreservesOneWeiAndLargeLedgerAmounts(){var tree=views.exact(Map.of("wei",new BigDecimal("9007199254740993"),"eth",new BigDecimal("0.123456789012345678"),"entries",List.of(Map.of("debit",new BigDecimal("999999999999.999999")))));assertTrue(tree.get("wei").isTextual());assertEquals("9007199254740993",tree.get("wei").asText());assertEquals("0.123456789012345678",tree.get("eth").asText());assertEquals("999999999999.999999",tree.get("entries").get(0).get("debit").asText());}
}
