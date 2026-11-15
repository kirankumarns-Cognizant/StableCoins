package com.cognizant.stablecoins.payment.api;

import com.cognizant.stablecoins.payment.service.PaymentService;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.Payment;
import com.cognizant.stablecoins.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import static com.cognizant.stablecoins.payment.support.IntentFixtures.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class PaymentIntentApiTest {
    static ValidatorFactory factory;
    MockMvc mvc;
    PaymentRepository payments;
    final ObjectMapper mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    @BeforeAll static void validator() { factory=Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidator() { factory.close(); }
    @BeforeEach void setUp() {
        payments=mock(PaymentRepository.class);
        PaymentService service=new PaymentService(payments,mock(AccountRepository.class),mock(BeneficiaryRepository.class),
            mock(PaymentStatusHistoryRepository.class),mock(OutboxEventRepository.class),mapper,
            new PaymentIntentService(factory.getValidator(),policies()));
        mvc=MockMvcBuilders.standaloneSetup(new PaymentController(service,new com.cognizant.stablecoins.payment.security.CustomerAccess(new com.cognizant.stablecoins.payment.security.CustomerAccessProperties()))).setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }
    @Test void rejectsIntentWithoutVerifiedServerContext() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key","key").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request()))).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(payments);
    }
    @Test void clientIdentityHeadersAndJsonCannotSupplyTrustedContext() throws Exception {
        var json=mapper.valueToTree(request());((com.fasterxml.jackson.databind.node.ObjectNode)json).put("customerId",CUSTOMER.toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode)json).put("requiredApprovals",0);
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key","key").header("X-Customer-Id",CUSTOMER)
            .header("X-Actor-Id","admin").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(json)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(payments);
    }
    @Test void nestedValidationRejectsMissingDeadlineBeforeService() throws Exception {
        var r=request();var i=r.getIntent();r.setIntent(new com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest(
            i.requiredOutcome(),null,i.maximumFee(),i.feeCurrency(),i.priority(),i.allowedAlternatives(),true));
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key","key").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(r))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(payments);
    }
    @Test void rejectsUnknownOutcome() throws Exception {
        String json=mapper.writeValueAsString(request()).replace("SIMULATED_BANK_INR","UNSUPPORTED_RAIL");
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key","key").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isBadRequest());verifyNoInteractions(payments);
    }
    @Test void legacyHttpAlsoRequiresVerifiedIdentity() throws Exception {
        var r=request();r.setIntent(null);
        Payment p=new Payment();p.setPaymentId("PAY-LEGACY");p.setCorrelationId("CORR-LEGACY");p.setStatus("COMPLIANCE_PENDING");
        String canonical=String.join("|",r.getSenderAccountId().toString(),r.getBeneficiaryId().toString(),"10000.00","USD","SUPPLIER_INVOICE","AUTO");
        p.setRequestFingerprint(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        when(payments.findByIdempotencyKey("old-key")).thenReturn(Optional.of(p));
        when(payments.findById("PAY-LEGACY")).thenReturn(Optional.of(p));
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key","old-key").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(r))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/payments/PAY-LEGACY")).andExpect(status().isUnauthorized());
        verify(payments,never()).save(any());
    }
}
