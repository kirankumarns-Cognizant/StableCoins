package com.cognizant.stablecoins.payment.service;

import com.cognizant.stablecoins.payment.api.dto.*;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.payment.domain.*;
import com.cognizant.stablecoins.payment.exception.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.cognizant.stablecoins.payment.support.IntentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentIntentCreationTest {
    @Mock PaymentRepository payments;
    @Mock AccountRepository accounts;
    @Mock BeneficiaryRepository beneficiaries;
    @Mock PaymentStatusHistoryRepository history;
    @Mock OutboxEventRepository outbox;
    static ValidatorFactory factory;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    RoutingPolicyProperties policies;
    PaymentService service;
    CreatePaymentRequest request;
    @BeforeAll static void validator() { factory=Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidator() { factory.close(); }
    @BeforeEach void setUp() {
        policies=policies();request=request();
        service=new PaymentService(payments,accounts,beneficiaries,history,outbox,mapper,
            new PaymentIntentService(factory.getValidator(),policies));
    }
    void ready() {
        when(payments.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accounts.findById(request.getSenderAccountId())).thenReturn(Optional.of(account()));
        when(beneficiaries.findById(request.getBeneficiaryId())).thenReturn(Optional.of(beneficiary()));
    }
    Payment created() {
        ready();service.createPayment("intent-key",request,ACTOR);
        ArgumentCaptor<Payment> c=ArgumentCaptor.forClass(Payment.class);verify(payments).save(c.capture());return c.getValue();
    }
    @Test void persistsIntentAndPropagatesInrToEventAndResponse() throws Exception {
        Payment p=created();assertThat(p.getSourceCurrency()).isEqualTo("USD");
        assertThat(p.getDestinationCurrency()).isEqualTo("INR");assertThat(p.getIntent().policy().requiredApprovals()).isEqualTo(2);
        ArgumentCaptor<OutboxEvent> c=ArgumentCaptor.forClass(OutboxEvent.class);verify(outbox).save(c.capture());
        var event=mapper.readTree(c.getValue().getPayload());assertThat(event.get("payload").get("destinationCurrency").asText()).isEqualTo("INR");
        when(payments.findById(p.getPaymentId())).thenReturn(Optional.of(p));
        assertThat(service.getPayment(p.getPaymentId(),ACTOR).getIntent()).isEqualTo(p.getIntent());
        verify(history,times(2)).save(any());
    }
    @Test void walletSettlementDoesNotMasqueradeAsUsdBankCredit() {
        request.setIntent(intent(BeneficiaryOutcome.USDC_WALLET));
        Payment p=created();assertThat(p.getDestinationCurrency()).isNull();assertThat(p.getIntent().settlementAsset()).isEqualTo(SettlementAsset.USDC);
    }
    @Test void publicLegacyEntryPointFailsClosedForNewIntent() {
        assertThatThrownBy(()->service.createPayment("key",request)).isInstanceOf(PaymentAccessException.class)
            .extracting("errorCode").isEqualTo("AUTHENTICATION_REQUIRED");verifyNoInteractions(payments,accounts,beneficiaries,history,outbox);
    }
    @Test void rejectsCrossCustomerBeforeAnyWrite() {
        ready();AuthorizedCustomer foreign=new AuthorizedCustomer(UUID.randomUUID(),"another-subject");
        assertThatThrownBy(()->service.createPayment("key",request,foreign)).isInstanceOf(PaymentAccessException.class);
        verify(payments,never()).save(any());verifyNoInteractions(history,outbox);
    }
    @Test void sameInstructionReplaysOriginalSnapshotAfterPolicyChange() {
        Payment p=created();policies.setPolicies(Map.of(CUSTOMER,policy("test-v2")));
        when(payments.findByIdempotencyKey("intent-key")).thenReturn(Optional.of(p));
        assertThat(service.createPayment("intent-key",request,ACTOR).getPaymentId()).isEqualTo(p.getPaymentId());
        assertThat(p.getIntent().policy().version()).isEqualTo("test-v1");verify(payments,times(1)).save(any());
    }
    @Test void replayRejectsAnotherCustomerEvenWithIdenticalPayload() {
        Payment p=created();when(payments.findByIdempotencyKey("intent-key")).thenReturn(Optional.of(p));
        assertThatThrownBy(()->service.createPayment("intent-key",request,new AuthorizedCustomer(UUID.randomUUID(),"other")))
            .isInstanceOf(PaymentAccessException.class).extracting("errorCode").isEqualTo("CUSTOMER_SCOPE_MISMATCH");
    }
    @ParameterizedTest @ValueSource(strings={"outcome","deadline","fee","priority","alternatives","confirmed"})
    void everyMaterialIntentChangeConflictsOnReplay(String field) {
        Payment p=created();if (!field.equals("confirmed")) {
            when(payments.findByIdempotencyKey("intent-key")).thenReturn(Optional.of(p));
        }
        PaymentIntentRequest i=request.getIntent();request.setIntent(new PaymentIntentRequest(
            field.equals("outcome")?BeneficiaryOutcome.SIMULATED_BANK_USD:i.requiredOutcome(),
            field.equals("deadline")?i.deliverBy().plusSeconds(1):i.deliverBy(),
            field.equals("fee")?new BigDecimal("24.00"):i.maximumFee(),i.feeCurrency(),
            field.equals("priority")?RoutingPriority.LOWEST_COST:i.priority(),
            field.equals("alternatives")?Set.of(BeneficiaryOutcome.USDC_WALLET):i.allowedAlternatives(),
            field.equals("confirmed")?false:i.confirmed()));
        assertThatThrownBy(()->service.createPayment("intent-key",request,ACTOR)).isInstanceOf(
            field.equals("confirmed")?PaymentValidationException.class:IdempotencyConflictException.class);
        verify(payments,times(1)).save(any());verify(outbox,times(1)).save(any());
    }
    @Test void normalizesDecimalAndSetOrderingInFingerprint() {
        PaymentIntentRequest i=request.getIntent();request.setIntent(new PaymentIntentRequest(i.requiredOutcome(),i.deliverBy(),
            new BigDecimal("25.0"),i.feeCurrency(),i.priority(),new LinkedHashSet<>(List.of(BeneficiaryOutcome.USDC_WALLET,BeneficiaryOutcome.SIMULATED_BANK_USD)),true));
        String first=service.computeFingerprint(request);
        request.setIntent(new PaymentIntentRequest(i.requiredOutcome(),i.deliverBy(),new BigDecimal("25.00"),i.feeCurrency(),i.priority(),
            new LinkedHashSet<>(List.of(BeneficiaryOutcome.SIMULATED_BANK_USD,BeneficiaryOutcome.USDC_WALLET)),true));
        assertThat(service.computeFingerprint(request)).isEqualTo(first);
    }
    @Test void legacyFingerprintRemainsByteForByteCompatible() throws Exception {
        request.setIntent(null);
        String canonical=String.join("|",request.getSenderAccountId().toString(),request.getBeneficiaryId().toString(),
            "10000.00","USD","SUPPLIER_INVOICE","AUTO");
        String original=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        assertThat(service.computeFingerprint(request)).isEqualTo(original);
        Payment p=created();assertThat(p.getIntent()).isNull();assertThat(p.getDestinationCurrency()).isEqualTo("USD");
    }
    @Test void readEndpointsRequireIdentityForIntentPayments() {
        Payment p=created();when(payments.findById(p.getPaymentId())).thenReturn(Optional.of(p));
        assertThatThrownBy(()->service.getPayment(p.getPaymentId())).isInstanceOf(PaymentAccessException.class);
        assertThatThrownBy(()->service.getPaymentStatus(p.getPaymentId())).isInstanceOf(PaymentAccessException.class);
        assertThatThrownBy(()->service.getPaymentJourney(p.getPaymentId())).isInstanceOf(PaymentAccessException.class);
        assertThatThrownBy(()->service.getPayment(p.getPaymentId(),new AuthorizedCustomer(UUID.randomUUID(),"foreign")))
            .isInstanceOf(PaymentAccessException.class);
        assertThat(service.getPaymentStatus(p.getPaymentId(),ACTOR).getStatus()).isEqualTo("COMPLIANCE_PENDING");
    }
    @Test void legacyPaymentsAreCustomerScopedForCreateReadAndReplay() {
        request.setIntent(null);Payment p=created();
        when(payments.findById(p.getPaymentId())).thenReturn(Optional.of(p));
        when(payments.findByIdempotencyKey("intent-key")).thenReturn(Optional.of(p));
        var foreign=new AuthorizedCustomer(UUID.randomUUID(),"foreign");
        assertThatThrownBy(()->service.getPayment(p.getPaymentId(),foreign)).isInstanceOf(PaymentAccessException.class);
        assertThatThrownBy(()->service.createPayment("intent-key",request,foreign)).isInstanceOf(PaymentAccessException.class);
        assertThatThrownBy(()->service.createPayment("different-key",request,foreign)).isInstanceOf(PaymentAccessException.class);
        assertThat(service.getPayment(p.getPaymentId(),ACTOR).getPaymentId()).isEqualTo(p.getPaymentId());
        verify(payments,times(1)).save(any());verify(outbox,times(1)).save(any());
    }
    @Test void snapshotRoundTripsThroughJsonUsedByHibernate() throws Exception {
        Payment p=created();String json=mapper.writeValueAsString(p.getIntent());
        assertThat(mapper.readValue(json,PaymentIntentSnapshot.class)).isEqualTo(p.getIntent());
    }
}
