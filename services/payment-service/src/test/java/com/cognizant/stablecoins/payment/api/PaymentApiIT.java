package com.cognizant.stablecoins.payment.api;

import com.cognizant.stablecoins.payment.api.dto.CreatePaymentResponse;
import com.cognizant.stablecoins.payment.api.dto.PaymentJourneyResponse;
import com.cognizant.stablecoins.payment.api.dto.PaymentResponse;
import com.cognizant.stablecoins.payment.api.dto.PaymentStatusResponse;
import com.cognizant.stablecoins.payment.model.Account;
import com.cognizant.stablecoins.payment.model.Beneficiary;
import com.cognizant.stablecoins.payment.model.Customer;
import com.cognizant.stablecoins.payment.repository.AccountRepository;
import com.cognizant.stablecoins.payment.repository.BeneficiaryRepository;
import com.cognizant.stablecoins.payment.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties="spring.cloud.aws.sqs.enabled=false")
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker=true)
class PaymentApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.cognizant.stablecoins.payment.event.publisher.DomainEventPublisher events;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private BeneficiaryRepository beneficiaryRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @Autowired private com.cognizant.stablecoins.payment.security.CustomerAccessProperties access;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private UUID accountId;
    private UUID beneficiaryId;

    @BeforeEach
    void setUp() {
        for (String table : java.util.List.of("routing_decision", "routing_audit", "routing_recommendation",
                "payment_outbox_event", "payment_status_history", "payment")) jdbc.execute("DELETE FROM " + table);
        beneficiaryRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = new Customer();
        customer.setName("Acme Corp");
        customer.setCustomerType("CORPORATE");
        customer = customerRepository.save(customer);
        access.setMemberships(Map.of("integration-subject", new com.cognizant.stablecoins.payment.security.CustomerAccessProperties.Membership(
            customer.getCustomerId(), java.util.Set.of(com.cognizant.stablecoins.payment.security.CustomerAccessProperties.Role.PAYMENT_OPERATOR))));
        org.mockito.Mockito.when(decoder.decode("integration-token")).thenReturn(org.springframework.security.oauth2.jwt.Jwt.withTokenValue("integration-token")
            .header("alg", "RS256").subject("integration-subject").issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(300)).build());
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of((request, body, execution) -> {
            request.getHeaders().setBearerAuth("integration-token"); return execution.execute(request, body);
        }));

        Account account = new Account();
        account.setCustomerId(customer.getCustomerId());
        account.setCurrency("USD");
        account.setAccountType("OPERATING");
        account.setBalance(new BigDecimal("100000.00"));
        account = accountRepository.save(account);
        accountId = account.getAccountId();

        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setCustomerId(customer.getCustomerId());
        beneficiary.setName("Supplier Ltd");
        beneficiary.setCountry("SGP");
        beneficiary.setCurrency("USD");
        beneficiary.setWalletAddress("0xABCDEF1234567890ABCDEF1234567890ABCDEF12");
        beneficiary = beneficiaryRepository.save(beneficiary);
        beneficiaryId = beneficiary.getBeneficiaryId();
    }

    @Test
    void createPayment_validRequest_returns201WithPaymentId() {
        HttpEntity<Map<String, Object>> request = buildRequest(accountId, beneficiaryId,
                "10000.00", "idempotency-key-001");

        ResponseEntity<CreatePaymentResponse> response =
                restTemplate.postForEntity("/api/v1/payments", request, CreatePaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPaymentId()).startsWith("PAY-");
        assertThat(response.getBody().getCorrelationId()).startsWith("CORR-");
        assertThat(response.getBody().getStatus()).isEqualTo("COMPLIANCE_PENDING");
    }

    @Test
    void createPayment_sameIdempotencyKeyAndPayload_returnsSamePaymentId() {
        HttpEntity<Map<String, Object>> request = buildRequest(accountId, beneficiaryId,
                "10000.00", "idempotency-key-002");

        ResponseEntity<CreatePaymentResponse> first =
                restTemplate.postForEntity("/api/v1/payments", request, CreatePaymentResponse.class);
        ResponseEntity<CreatePaymentResponse> second =
                restTemplate.postForEntity("/api/v1/payments", request, CreatePaymentResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().getPaymentId()).isEqualTo(second.getBody().getPaymentId());
    }

    @Test
    void createPayment_sameIdempotencyKeyDifferentPayload_returns409() {
        HttpEntity<Map<String, Object>> first = buildRequest(accountId, beneficiaryId,
                "10000.00", "idempotency-key-003");
        HttpEntity<Map<String, Object>> second = buildRequest(accountId, beneficiaryId,
                "5000.00", "idempotency-key-003");

        restTemplate.postForEntity("/api/v1/payments", first, CreatePaymentResponse.class);
        ResponseEntity<Map> conflictResponse =
                restTemplate.postForEntity("/api/v1/payments", second, Map.class);

        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflictResponse.getBody().get("code")).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void createPayment_missingIdempotencyKey_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "senderAccountId", accountId.toString(),
                "beneficiaryId", beneficiaryId.toString(),
                "amount", "10000.00",
                "currency", "USD",
                "purpose", "SUPPLIER_INVOICE",
                "requestedRail", "AUTO");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/v1/payments", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPayment_nonExistentAccount_returns404() {
        HttpEntity<Map<String, Object>> request = buildRequest(UUID.randomUUID(), beneficiaryId,
                "10000.00", "idempotency-key-005");

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/v1/payments", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void getPayment_existingPayment_returnsFullPaymentDetail() {
        HttpEntity<Map<String, Object>> request = buildRequest(accountId, beneficiaryId,
                "10000.00", "idempotency-key-006");
        CreatePaymentResponse created =
                restTemplate.postForEntity("/api/v1/payments", request, CreatePaymentResponse.class)
                        .getBody();

        ResponseEntity<PaymentResponse> response =
                restTemplate.getForEntity("/api/v1/payments/" + created.getPaymentId(),
                        PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPaymentId()).isEqualTo(created.getPaymentId());
        assertThat(response.getBody().getStatus()).isEqualTo("COMPLIANCE_PENDING");
        assertThat(response.getBody().getAmount()).isEqualByComparingTo("10000.00");
    }

    @Test
    void getPaymentStatus_returnsCurrentStatus() {
        HttpEntity<Map<String, Object>> request = buildRequest(accountId, beneficiaryId,
                "10000.00", "idempotency-key-007");
        CreatePaymentResponse created =
                restTemplate.postForEntity("/api/v1/payments", request, CreatePaymentResponse.class)
                        .getBody();

        ResponseEntity<PaymentStatusResponse> response =
                restTemplate.getForEntity("/api/v1/payments/" + created.getPaymentId() + "/status",
                        PaymentStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo("COMPLIANCE_PENDING");
    }

    @Test
    void getPaymentJourney_returnsStatusHistory() {
        HttpEntity<Map<String, Object>> request = buildRequest(accountId, beneficiaryId,
                "10000.00", "idempotency-key-008");
        CreatePaymentResponse created =
                restTemplate.postForEntity("/api/v1/payments", request, CreatePaymentResponse.class)
                        .getBody();

        ResponseEntity<PaymentJourneyResponse> response =
                restTemplate.getForEntity("/api/v1/payments/" + created.getPaymentId() + "/journey",
                        PaymentJourneyResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSteps()).hasSize(2);
        assertThat(response.getBody().getSteps().get(0).getToStatus()).isEqualTo("CREATED");
        assertThat(response.getBody().getSteps().get(0).getFromStatus()).isNull();
    }

    @Test
    void getPayment_nonExistentId_returns404() {
        ResponseEntity<Map> response =
                restTemplate.getForEntity("/api/v1/payments/PAY-DOESNOTEXIST", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_NOT_FOUND");
    }

    private HttpEntity<Map<String, Object>> buildRequest(UUID acctId, UUID benId,
                                                          String amount, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        Map<String, Object> body = Map.of(
                "senderAccountId", acctId.toString(),
                "beneficiaryId", benId.toString(),
                "amount", amount,
                "currency", "USD",
                "purpose", "SUPPLIER_INVOICE",
                "requestedRail", "AUTO");
        return new HttpEntity<>(body, headers);
    }
}
