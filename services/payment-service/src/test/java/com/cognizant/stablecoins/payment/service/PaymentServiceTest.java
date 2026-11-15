package com.cognizant.stablecoins.payment.service;

import com.cognizant.stablecoins.payment.api.dto.CreatePaymentRequest;
import com.cognizant.stablecoins.payment.api.dto.CreatePaymentResponse;
import com.cognizant.stablecoins.payment.domain.PaymentPurpose;
import com.cognizant.stablecoins.payment.domain.PaymentRail;
import com.cognizant.stablecoins.payment.domain.PaymentStatus;
import com.cognizant.stablecoins.payment.domain.SupportedCurrency;
import com.cognizant.stablecoins.payment.exception.*;
import com.cognizant.stablecoins.payment.model.Account;
import com.cognizant.stablecoins.payment.model.Beneficiary;
import com.cognizant.stablecoins.payment.model.Payment;
import com.cognizant.stablecoins.payment.model.OutboxEvent;
import com.cognizant.stablecoins.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private PaymentStatusHistoryRepository historyRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    private UUID accountId;
    private UUID beneficiaryId;
    private Account activeAccount;
    private Beneficiary activeBeneficiary;
    private CreatePaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        beneficiaryId = UUID.randomUUID();

        activeAccount = new Account();
        activeAccount.setAccountId(accountId);
        activeAccount.setStatus("ACTIVE");
        activeAccount.setCurrency("USD");

        activeBeneficiary = new Beneficiary();
        activeBeneficiary.setBeneficiaryId(beneficiaryId);
        activeBeneficiary.setStatus("ACTIVE");

        validRequest = new CreatePaymentRequest();
        validRequest.setSenderAccountId(accountId);
        validRequest.setBeneficiaryId(beneficiaryId);
        validRequest.setAmount(new BigDecimal("10000.00"));
        validRequest.setCurrency(SupportedCurrency.USD);
        validRequest.setPurpose(PaymentPurpose.SUPPLIER_INVOICE);
        validRequest.setRequestedRail(PaymentRail.AUTO);
    }

    @Test
    void createPayment_newKey_returnsCompliancePendingPayment() throws Exception {
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(activeBeneficiary));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"test\"}");

        CreatePaymentResponse response = paymentService.createPayment("key-001", validRequest);

        assertThat(response.getPaymentId()).startsWith("PAY-");
        assertThat(response.getCorrelationId()).startsWith("CORR-");
        // Payment now starts at COMPLIANCE_PENDING (outbox written atomically)
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLIANCE_PENDING.name());
        verify(outboxEventRepository).save(argThat(o -> "PaymentCreated".equals(o.getEventType())));
    }

    @Test
    void createPayment_sameKeyAndSamePayload_returnsExistingPayment() {
        Payment existing = buildSavedPayment("PAY-EXISTING", "CORR-EXISTING");
        String fingerprint = paymentService.computeFingerprint(validRequest);
        existing.setRequestFingerprint(fingerprint);

        when(paymentRepository.findByIdempotencyKey("key-001")).thenReturn(Optional.of(existing));

        CreatePaymentResponse response = paymentService.createPayment("key-001", validRequest);

        assertThat(response.getPaymentId()).isEqualTo("PAY-EXISTING");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_sameKeyDifferentPayload_throwsIdempotencyConflict() {
        Payment existing = buildSavedPayment("PAY-EXISTING", "CORR-EXISTING");
        existing.setRequestFingerprint("different-fingerprint");

        when(paymentRepository.findByIdempotencyKey("key-001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.createPayment("key-001", validRequest))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("key-001");
    }

    @Test
    void createPayment_accountNotFound_throwsAccountNotFoundException() {
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment("key-002", validRequest))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void createPayment_accountSuspended_throwsValidationException() {
        activeAccount.setStatus("SUSPENDED");
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> paymentService.createPayment("key-003", validRequest))
                .isInstanceOf(PaymentValidationException.class)
                .extracting("errorCode").isEqualTo("ACCOUNT_NOT_ACTIVE");
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void createPayment_beneficiaryNotFound_throwsBeneficiaryNotFoundException() {
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment("key-004", validRequest))
                .isInstanceOf(BeneficiaryNotFoundException.class);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void createPayment_beneficiarySuspended_throwsValidationException() {
        activeBeneficiary.setStatus("SUSPENDED");
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(activeBeneficiary));

        assertThatThrownBy(() -> paymentService.createPayment("key-005", validRequest))
                .isInstanceOf(PaymentValidationException.class)
                .extracting("errorCode").isEqualTo("BENEFICIARY_NOT_ACTIVE");
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void getPayment_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.findById("PAY-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment("PAY-MISSING"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void createPayment_persistsBothStatusHistoryEntries() throws Exception {
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(activeBeneficiary));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"test\"}");

        paymentService.createPayment("key-006", validRequest);

        ArgumentCaptor<com.cognizant.stablecoins.payment.model.PaymentStatusHistory> captor =
                ArgumentCaptor.forClass(com.cognizant.stablecoins.payment.model.PaymentStatusHistory.class);
        // Now saves TWO history entries: CREATED then COMPLIANCE_PENDING
        verify(historyRepository, times(2)).save(captor.capture());
        List<com.cognizant.stablecoins.payment.model.PaymentStatusHistory> histories = captor.getAllValues();

        assertThat(histories.get(0).getOldStatus()).isNull();
        assertThat(histories.get(0).getNewStatus()).isEqualTo(PaymentStatus.CREATED.name());
        assertThat(histories.get(1).getOldStatus()).isEqualTo(PaymentStatus.CREATED.name());
        assertThat(histories.get(1).getNewStatus()).isEqualTo(PaymentStatus.COMPLIANCE_PENDING.name());
    }

    @Test
    void createPayment_writesOutboxEventAtomically() throws Exception {
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(activeBeneficiary));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"e1\",\"eventType\":\"PaymentCreated\"}");

        paymentService.createPayment("key-007", validRequest);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();

        assertThat(outbox.getEventType()).isEqualTo("PaymentCreated");
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getEventId()).isNotBlank();
        assertThat(outbox.getCorrelationId()).startsWith("CORR-");
    }

    @Test
    void createPayment_objectMapperFailure_rollsBackTransaction() throws Exception {
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(activeBeneficiary));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("fail") {});

        assertThatThrownBy(() -> paymentService.createPayment("key-008", validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("outbox");
    }

    @Test
    void computeFingerprint_isDeterministic() {
        String f1 = paymentService.computeFingerprint(validRequest);
        String f2 = paymentService.computeFingerprint(validRequest);
        assertThat(f1).isEqualTo(f2).hasSize(64);
    }

    private Payment buildSavedPayment(String paymentId, String correlationId) {
        Payment p = new Payment();
        p.setPaymentId(paymentId);
        p.setCorrelationId(correlationId);
        p.setStatus(PaymentStatus.CREATED.name());
        p.setCreatedAt(OffsetDateTime.now());
        p.setUpdatedAt(OffsetDateTime.now());
        return p;
    }
}
