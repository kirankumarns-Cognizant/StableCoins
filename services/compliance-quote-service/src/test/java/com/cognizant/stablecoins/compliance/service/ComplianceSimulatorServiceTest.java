package com.cognizant.stablecoins.compliance.service;

import com.cognizant.stablecoins.compliance.event.DomainEvent;
import com.cognizant.stablecoins.compliance.event.PaymentCreatedPayload;
import com.cognizant.stablecoins.compliance.model.ComplianceCheck;
import com.cognizant.stablecoins.compliance.model.OutboxEvent;
import com.cognizant.stablecoins.compliance.repository.ComplianceCheckRepository;
import com.cognizant.stablecoins.compliance.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceSimulatorServiceTest {

    @Mock private ComplianceCheckRepository complianceCheckRepository;
    @Mock private OutboxEventRepository outboxEventRepository;

    private ComplianceSimulatorService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new ComplianceSimulatorService(complianceCheckRepository, outboxEventRepository, objectMapper);
        when(complianceCheckRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Standard USD 10,000 SUPPLIER_INVOICE: all PASS → APPROVED ──────────────

    @Test
    void standardPayment_allCheckPass_overallApproved() {
        DomainEvent<PaymentCreatedPayload> event = buildEvent("PAY-001", "CORR-001",
                new BigDecimal("10000.00"), "SUPPLIER_INVOICE");

        service.runChecks(event);

        ArgumentCaptor<ComplianceCheck> captor = ArgumentCaptor.forClass(ComplianceCheck.class);
        verify(complianceCheckRepository, times(4)).save(captor.capture());

        List<ComplianceCheck> checks = captor.getAllValues();
        assertThat(checks).allMatch(c -> "PASS".equals(c.getResult()));
        assertThat(checks).allMatch(ComplianceCheck::isSimulation);
        assertThat(checks.stream().map(ComplianceCheck::getCheckType).toList())
                .containsExactly("KYC_KYB", "SANCTIONS", "AML", "WALLET_SCREENING");
    }

    @Test
    void standardPayment_writesComplianceCompletedOutbox_withApproved() {
        DomainEvent<PaymentCreatedPayload> event = buildEvent("PAY-002", "CORR-002",
                new BigDecimal("10000.00"), "SUPPLIER_INVOICE");

        service.runChecks(event);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();

        assertThat(outbox.getEventType()).isEqualTo("ComplianceCompleted");
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getAggregateId()).isEqualTo("PAY-002");
        assertThat(outbox.getCorrelationId()).isEqualTo("CORR-002");
        assertThat(outbox.getPayload()).contains("APPROVED");
        assertThat(outbox.getPayload()).contains("\"simulation\":true");
    }

    // ── AML rejection: amount > 1,000,000 ──────────────────────────────────────

    @Test
    void highValuePayment_amlFail_overallRejected() {
        DomainEvent<PaymentCreatedPayload> event = buildEvent("PAY-003", "CORR-003",
                new BigDecimal("1500000.00"), "SUPPLIER_INVOICE");

        service.runChecks(event);

        ArgumentCaptor<ComplianceCheck> checkCaptor = ArgumentCaptor.forClass(ComplianceCheck.class);
        verify(complianceCheckRepository, times(4)).save(checkCaptor.capture());

        ComplianceCheck amlCheck = checkCaptor.getAllValues().stream()
                .filter(c -> "AML".equals(c.getCheckType()))
                .findFirst().orElseThrow();
        assertThat(amlCheck.getResult()).isEqualTo("FAIL");
        assertThat(amlCheck.isSimulation()).isTrue();

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getPayload()).contains("REJECTED");
    }

    // ── Sanctions rejection: purpose == REJECTED_TEST ──────────────────────────

    @Test
    void rejectedTestPurpose_sanctionsFail_overallRejected() {
        DomainEvent<PaymentCreatedPayload> event = buildEvent("PAY-004", "CORR-004",
                new BigDecimal("10000.00"), "REJECTED_TEST");

        service.runChecks(event);

        ArgumentCaptor<ComplianceCheck> checkCaptor = ArgumentCaptor.forClass(ComplianceCheck.class);
        verify(complianceCheckRepository, times(4)).save(checkCaptor.capture());

        ComplianceCheck sanctionsCheck = checkCaptor.getAllValues().stream()
                .filter(c -> "SANCTIONS".equals(c.getCheckType()))
                .findFirst().orElseThrow();
        assertThat(sanctionsCheck.getResult()).isEqualTo("FAIL");
        assertThat(sanctionsCheck.isSimulation()).isTrue();
    }

    // ── Determinism ─────────────────────────────────────────────────────────────

    @Test
    void sameInput_sameOutcome_deterministic() {
        DomainEvent<PaymentCreatedPayload> e1 = buildEvent("PAY-005", "CORR-005",
                new BigDecimal("10000.00"), "SUPPLIER_INVOICE");
        DomainEvent<PaymentCreatedPayload> e2 = buildEvent("PAY-006", "CORR-006",
                new BigDecimal("10000.00"), "SUPPLIER_INVOICE");

        service.runChecks(e1);
        service.runChecks(e2);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(captor.capture());

        List<OutboxEvent> outboxes = captor.getAllValues();
        assertThat(outboxes.get(0).getPayload()).contains("APPROVED");
        assertThat(outboxes.get(1).getPayload()).contains("APPROVED");
    }

    // ── Simulation flag always true ─────────────────────────────────────────────

    @Test
    void allChecks_simulationFlagIsAlwaysTrue() {
        DomainEvent<PaymentCreatedPayload> event = buildEvent("PAY-007", "CORR-007",
                new BigDecimal("10000.00"), "SUPPLIER_INVOICE");

        service.runChecks(event);

        ArgumentCaptor<ComplianceCheck> captor = ArgumentCaptor.forClass(ComplianceCheck.class);
        verify(complianceCheckRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(ComplianceCheck::isSimulation);
    }

    // ── Correlation ID propagation ──────────────────────────────────────────────

    @Test
    void correlationId_propagatedToAllChecksAndOutbox() {
        String correlationId = "CORR-PROPAGATE";
        DomainEvent<PaymentCreatedPayload> event = buildEvent("PAY-008", correlationId,
                new BigDecimal("5000.00"), "TRADE_SETTLEMENT");

        service.runChecks(event);

        ArgumentCaptor<ComplianceCheck> checkCaptor = ArgumentCaptor.forClass(ComplianceCheck.class);
        verify(complianceCheckRepository, times(4)).save(checkCaptor.capture());
        assertThat(checkCaptor.getAllValues()).allMatch(c -> correlationId.equals(c.getCorrelationId()));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getCorrelationId()).isEqualTo(correlationId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private DomainEvent<PaymentCreatedPayload> buildEvent(String paymentId, String correlationId,
                                                           BigDecimal amount, String purpose) {
        PaymentCreatedPayload payload = new PaymentCreatedPayload();
        payload.setSenderAccountId(UUID.randomUUID());
        payload.setBeneficiaryId(UUID.randomUUID());
        payload.setAmount(amount);
        payload.setSourceCurrency("USD");
        payload.setDestinationCurrency("USD");
        payload.setPurpose(purpose);
        payload.setRequestedRail("AUTO");

        return new DomainEvent<>(
                UUID.randomUUID().toString(),
                "PaymentCreated",
                "1",
                paymentId,
                correlationId,
                payload
        );
    }
}
