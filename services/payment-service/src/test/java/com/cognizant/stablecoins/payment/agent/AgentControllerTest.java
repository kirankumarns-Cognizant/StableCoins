package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AgentController.class) @Import({PaymentSecurityConfiguration.class,CustomerAccess.class,CustomerAccessProperties.class})
class AgentControllerTest {
    @Autowired MockMvc mvc;@Autowired CustomerAccessProperties access;@MockBean AgentRunService service;@MockBean JwtDecoder decoder;
    String path="/api/v1/payments/PAY-AGENT/recommendations/"+SEED+"/agent-runs";
    @BeforeEach void setup(){access.setMemberships(Map.of(ACTOR.actorId(),new CustomerAccessProperties.Membership(ACTOR.customerId(),Set.of(CustomerAccessProperties.Role.PAYMENT_OPERATOR))));}
    @Test void noVerifiedTokenCannotInvokeAgent() throws Exception {mvc.perform(post(path).header("Idempotency-Key","test").contentType("application/json").content("{\"instruction\":\"explain\"}"))
        .andExpect(status().isUnauthorized());verifyNoInteractions(service);}
    @Test void unmappedSubjectCannotClaimCustomer() throws Exception {mvc.perform(post(path).with(jwt().jwt(j->j.subject("other").claim("customerId",ACTOR.customerId().toString())))
        .header("Idempotency-Key","test").contentType("application/json").content("{\"instruction\":\"explain\"}"))
        .andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test void operatorCanStartBoundedAsyncRun() throws Exception {var now=java.time.Instant.now();var view=new AgentRunView(UUID.randomUUID(),"PAY-AGENT","CORR-AGENT",SEED,RoutingAgentRun.Status.RUNNING,"configured-model",RoutingAgent.PROMPT_VERSION,now,now.plusSeconds(90),null,null,null);
        when(service.start("PAY-AGENT",SEED,ACTOR,"test","explain")).thenReturn(view);
        mvc.perform(post(path).with(jwt().jwt(j->j.subject(ACTOR.actorId()))).header("Idempotency-Key","test").contentType("application/json").content("{\"instruction\":\"explain\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("RUNNING"));verify(service).start("PAY-AGENT",SEED,ACTOR,"test","explain");}
    @Test void oversizedInstructionIsRejectedBeforeAdmission() throws Exception {mvc.perform(post(path).with(jwt().jwt(j->j.subject(ACTOR.actorId()))).header("Idempotency-Key","test")
        .contentType("application/json").content("{\"instruction\":\""+"x".repeat(2001)+"\"}"))
        .andExpect(status().isBadRequest());verifyNoInteractions(service);}
    @Test void missingIdempotencyKeyIsRejected() throws Exception {mvc.perform(post(path).with(jwt().jwt(j->j.subject(ACTOR.actorId())))
        .contentType("application/json").content("{\"instruction\":\"explain\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
    @Test void disabledAgentReturnsStructuredError() throws Exception {when(service.start(anyString(),any(),any(),anyString(),anyString())).thenThrow(new AgentFailure("AGENT_DISABLED"));
        mvc.perform(post(path).with(jwt().jwt(j->j.subject(ACTOR.actorId()))).header("Idempotency-Key","test").contentType("application/json").content("{\"instruction\":\"explain\"}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AGENT_DISABLED"));}
}
