package com.cognizant.stablecoins.payment.security;
import com.cognizant.stablecoins.payment.api.*;
import com.cognizant.stablecoins.payment.approval.RecommendationService;
import com.cognizant.stablecoins.payment.service.PaymentService;
import com.cognizant.stablecoins.payment.api.dto.PaymentStatusResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.*;
import static com.cognizant.stablecoins.payment.support.IntentFixtures.CUSTOMER;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({PaymentController.class,RecommendationController.class})
@Import({PaymentSecurityConfiguration.class,CustomerAccess.class,CustomerAccessProperties.class})
class PaymentAccessSecurityTest {
    @Autowired MockMvc mvc; @Autowired CustomerAccessProperties memberships;
    @MockBean PaymentService payments; @MockBean RecommendationService recommendations; @MockBean JwtDecoder decoder;
    @BeforeEach void setup() { memberships.setMemberships(Map.of("operator",new CustomerAccessProperties.Membership(CUSTOMER,Set.of(PAYMENT_OPERATOR)))); }
    @Test void missingTokenCannotUseSpoofedIdentityHeaders() throws Exception {
        mvc.perform(get("/api/v1/payments/PAY-X/status").header("X-Customer-Id",CUSTOMER).header("X-Actor-Id","operator"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));verifyNoInteractions(payments,recommendations);
    }
    @Test void invalidBearerIsRejectedWithoutLeakingDecoderDetails() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("internal-secret-detail"));
        mvc.perform(get("/api/v1/payments/PAY-X/status").header("Authorization","Bearer invalid"))
            .andExpect(status().isUnauthorized()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-secret-detail"))));verifyNoInteractions(payments);
    }
    @Test void unmappedSubjectIsForbiddenEvenWithCustomerClaim() throws Exception {
        mvc.perform(get("/api/v1/payments/PAY-X/status").with(jwt().jwt(j->j.subject("unmapped").claim("customerId",CUSTOMER.toString()))))
            .andExpect(status().isForbidden());verifyNoInteractions(payments);
    }
    @Test void verifiedSubjectUsesServerMembership() throws Exception {
        when(payments.getPaymentStatus(eq("PAY-X"),any())).thenReturn(new PaymentStatusResponse("PAY-X","CORR-X","COMPLIANCE_APPROVED",null));
        mvc.perform(get("/api/v1/payments/PAY-X/status").with(jwt().jwt(j->j.subject("operator").claim("customerId",UUID.randomUUID().toString()))))
            .andExpect(status().isOk());verify(payments).getPaymentStatus("PAY-X",new com.cognizant.stablecoins.payment.intent.AuthorizedCustomer(CUSTOMER,"operator"));
    }
    @Test void operatorCannotGrantOwnApproverRoleWithJwtClaims() throws Exception {
        mvc.perform(post("/api/v1/payments/PAY-X/recommendations/"+UUID.randomUUID()+"/approvals")
            .with(jwt().jwt(j->j.subject("operator").claim("roles",List.of("PAYMENT_APPROVER"))))
            .contentType("application/json").content("{\"expectedVersion\":1,\"quoteId\":\""+UUID.randomUUID()+"\",\"quoteVersion\":1}"))
            .andExpect(status().isForbidden());verifyNoInteractions(recommendations);
    }
    @Test void approverCanSubmitTypedDecision() throws Exception {
        memberships.setMemberships(Map.of("approver",new CustomerAccessProperties.Membership(CUSTOMER,Set.of(PAYMENT_APPROVER))));
        UUID id=UUID.randomUUID(),quote=UUID.randomUUID();
        mvc.perform(post("/api/v1/payments/PAY-X/recommendations/"+id+"/approvals").with(jwt().jwt(j->j.subject("approver")))
            .contentType("application/json").content("{\"expectedVersion\":1,\"quoteId\":\""+quote+"\",\"quoteVersion\":2}"))
            .andExpect(status().isOk());
        verify(recommendations).approve("PAY-X",id,1,quote,2,new com.cognizant.stablecoins.payment.intent.AuthorizedCustomer(CUSTOMER,"approver"));
    }
    @Test void malformedUuidHasStructuredError() throws Exception {
        mvc.perform(get("/api/v1/payments/PAY-X/recommendations/not-a-uuid").with(jwt().jwt(j->j.subject("operator"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void membershipRemovalIsEffectiveOnNextRequest() throws Exception {
        memberships.setMemberships(Map.of());mvc.perform(get("/api/v1/payments/PAY-X/status").with(jwt().jwt(j->j.subject("operator"))))
            .andExpect(status().isForbidden());verifyNoInteractions(payments);
    }
}
