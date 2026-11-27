package com.cognizant.stablecoins.payment.service;

import com.cognizant.stablecoins.flow.SettlementNetwork.*;
import com.cognizant.stablecoins.payment.api.dto.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.cognizant.stablecoins.payment.settlement.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.cognizant.stablecoins.payment.support.IntentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExplicitSettlementCreationTest {
    final PaymentRepository payments = mock(PaymentRepository.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final BeneficiaryRepository beneficiaries = mock(BeneficiaryRepository.class);
    final PaymentStatusHistoryRepository history = mock(PaymentStatusHistoryRepository.class);
    final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    final PaymentIntentService intents = mock(PaymentIntentService.class);
    final PaymentService service = new PaymentService(payments, accounts, beneficiaries, history, outbox,
            new ObjectMapper().findAndRegisterModules(), intents);
    CreatePaymentRequest request;

    @BeforeEach void setup() {
        request = request();
        request.setIntent(intent(BeneficiaryOutcome.USDC_WALLET));
    }
    void select(String network, String address, String amount) {
        var old = request.getIntent();
        request.setIntent(new PaymentIntentRequest(old.requiredOutcome(), old.deliverBy(), old.maximumFee(),
                old.feeCurrency(), old.priority(), old.allowedAlternatives(), old.confirmed(),
                new SettlementSelection(network, address, new BigDecimal(amount))));
    }
    @Test void omittedSelectionPreservesHistoricalFingerprintExactly() throws Exception {
        String before = service.computeFingerprint(request);
        String canonical = String.join("|", request.getSenderAccountId().toString(), request.getBeneficiaryId().toString(),
                "10000.00", "USD", request.getPurpose().name(), request.getRequestedRail().name())
                + "|intent-v1|" + String.join("|", "USDC_WALLET", DEADLINE.toString(), "25.00",
                        "USD", "MEET_DEADLINE", "", "true");
        String expected = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(before).isEqualTo(expected);
    }
    @Test void selectionAndEveryCustomerSelectedTermChangeFingerprint() {
        String legacy = service.computeFingerprint(request);
        select("base-sepolia", "0x" + "a".repeat(40), "10000");
        String bound = service.computeFingerprint(request);
        assertThat(bound).isNotEqualTo(legacy);
        select("ethereum-sepolia", "0x" + "a".repeat(40), "10000");
        assertThat(service.computeFingerprint(request)).isNotEqualTo(bound);
        select("base-sepolia", "0x" + "b".repeat(40), "10000");
        assertThat(service.computeFingerprint(request)).isNotEqualTo(bound);
        select("base-sepolia", "0x" + "a".repeat(40), "9999");
        assertThat(service.computeFingerprint(request)).isNotEqualTo(bound);
        select("base-sepolia", "0x" + "A".repeat(40), "10000.000000");
        assertThat(service.computeFingerprint(request)).isEqualTo(bound);
    }
    @Test void selectedInstructionIsCapturedAfterOwnerFlushBeforeAnyEvent() {
        select("base-sepolia", "0x" + "a".repeat(40), "10000");
        var chain = new Chain("base-sepolia", 84532, "0x" + "b".repeat(64), Classification.PUBLIC_TESTNET);
        var context = new Context(new Token(chain, request.getIntent().settlement().tokenContract(), "USDC", 6,
                "0x" + "c".repeat(64)), new NativeAsset(chain, "ETH", 18),
                "0x" + "d".repeat(40), FeeModel.BASE_L1_AND_EXECUTION, "v1");
        var snapshot = new PaymentIntentSnapshot(UUID.randomUUID(), 1, CUSTOMER, ACTOR.actorId(), Instant.now(),
                SettlementAsset.USDC, true, false, request.getIntent(), policy("v1"),
                new ConfirmedSettlement(context, request.getAmount(), beneficiary().getWalletAddress()));
        when(accounts.findById(request.getSenderAccountId())).thenReturn(Optional.of(account()));
        when(beneficiaries.findById(request.getBeneficiaryId())).thenReturn(Optional.of(beneficiary()));
        when(intents.prepare(request, account(), beneficiary(), ACTOR)).thenReturn(snapshot);
        // Fixtures produce distinct model instances: match by request and actor instead.
        when(intents.prepare(eq(request), any(Account.class), any(Beneficiary.class), eq(ACTOR))).thenReturn(snapshot);
        service.createPayment("bound-key", request, ACTOR);
        var order = inOrder(payments, intents, outbox);
        order.verify(payments).findByIdempotencyKey("bound-key");
        order.verify(intents).prepare(eq(request), any(), any(), eq(ACTOR));
        order.verify(payments).save(any(Payment.class));
        order.verify(payments).flush();
        order.verify(intents).captureSettlement(any(Payment.class));
        order.verify(outbox).save(any(OutboxEvent.class));
    }
    @Test void failedBindingCaptureDoesNotPublishPaymentCreated() {
        select("base-sepolia", "0x" + "a".repeat(40), "10000");
        var snapshot = new PaymentIntentSnapshot(UUID.randomUUID(), 1, CUSTOMER, ACTOR.actorId(), Instant.now(),
                SettlementAsset.USDC, true, false, request.getIntent(), policy("v1"));
        when(accounts.findById(request.getSenderAccountId())).thenReturn(Optional.of(account()));
        when(beneficiaries.findById(request.getBeneficiaryId())).thenReturn(Optional.of(beneficiary()));
        when(intents.prepare(eq(request), any(), any(), eq(ACTOR))).thenReturn(snapshot);
        doThrow(new IllegalStateException("SETTLEMENT_BINDING_CONFLICT")).when(intents).captureSettlement(any());
        assertThatThrownBy(() -> service.createPayment("bound-key", request, ACTOR))
                .hasMessage("SETTLEMENT_BINDING_CONFLICT");
        verifyNoInteractions(outbox, history);
        // Unit boundary proves ordering, not actual DB rollback; PostgreSQL acceptance remains separate.
    }
}
