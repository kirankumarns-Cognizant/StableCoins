package com.cognizant.stablecoins.payment.workbench;
import com.cognizant.stablecoins.payment.api.WorkbenchController;
import com.cognizant.stablecoins.payment.security.*;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(value=WorkbenchController.class,properties="app.flow.enabled=true")
@Import({PaymentSecurityConfiguration.class,CustomerAccess.class,CustomerAccessProperties.class})
class WorkbenchControllerTest {
 @Autowired MockMvc mvc;@Autowired CustomerAccessProperties access;
 @MockBean WorkbenchService service;@MockBean JwtDecoder decoder;
 final UUID customer=UUID.fromString("11111111-1111-1111-1111-111111111111");
 @BeforeEach void setup(){access.setMemberships(Map.of("operator",new CustomerAccessProperties.Membership(customer,Set.of(CustomerAccessProperties.Role.PAYMENT_OPERATOR)),"approver-only",new CustomerAccessProperties.Membership(customer,Set.of(CustomerAccessProperties.Role.PAYMENT_APPROVER))));}
 @Test void anonymousCannotReadCatalog()throws Exception{mvc.perform(get("/api/v1/payments/workbench")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void claimedCustomerAndRoleDoNotGrantMembership()throws Exception{mvc.perform(get("/api/v1/payments/PAY-OTHER/evidence").with(jwt().jwt(j->j.subject("unmapped").claim("customerId",customer.toString()).claim("roles",List.of("PAYMENT_OPERATOR")))).header("X-Customer-Id",customer).header("X-Role","PAYMENT_OPERATOR")).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void approverNeedsOperatorReadPermission()throws Exception{mvc.perform(get("/api/v1/payments/workbench").with(jwt().jwt(j->j.subject("approver-only")))).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void verifiedSubjectResolvesOnlyServerMembership()throws Exception{when(service.catalog(new AuthorizedCustomer(customer,"operator"))).thenReturn(Map.of("customerId",customer));mvc.perform(get("/api/v1/payments/workbench").with(jwt().jwt(j->j.subject("operator").claim("customerId",UUID.randomUUID().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(customer.toString())).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));verify(service).catalog(new AuthorizedCustomer(customer,"operator"));}
 @Test void evidenceReadCannotBeUsedAsMutationEndpoint()throws Exception{mvc.perform(post("/api/v1/payments/PAY-1/evidence").with(jwt().jwt(j->j.subject("operator")))) .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED")).andExpect(header().string("Allow",org.hamcrest.Matchers.containsString("GET")));verifyNoInteractions(service);}
}
