package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.payment.settlement.BindingFixtures.*;

class SettlementQuoteServiceTest {
    final SettlementPreparationService preparations = mock(SettlementPreparationService.class);
    final SettlementQuoteStore store = mock(SettlementQuoteStore.class);
    final SettlementQuoteProperties properties = new SettlementQuoteProperties();
    final SettlementSelectionProperties selections = new SettlementSelectionProperties();
    final Instant now = Instant.parse("2026-11-25T10:00:00Z");
    final SettlementBinding.Instruction instruction = instruction();
    final AuthorizedCustomer actor = new AuthorizedCustomer(instruction.customerId(), "operator");
    final SettlementPreparation.Request request = new SettlementPreparation.Request(UUID.randomUUID(), instruction, now);
    final UUID reviewId = UUID.randomUUID();
    final SettlementObservation observation = new SettlementObservation(instruction, 100, BigDecimal.TEN, BigDecimal.ONE,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1e-18"), now, now.plusSeconds(15));
    final SettlementPreparation.Evidence evidence = new SettlementPreparation.Evidence(request, observation);
    final SettlementAvailability availability = new SettlementAvailability(evidence, BigDecimal.ZERO, BigDecimal.ZERO, now);
    final SettlementPreparationService.View view = new SettlementPreparationService.View(request, "OBSERVED_NOT_READY",
            "RESERVATION_CLAIM_INTEGRATION_PENDING", evidence, availability);
    final Map<UUID, SettlementQuote> rows = new HashMap<>();
    final SettlementQuoteService service = service(Clock.fixed(now, ZoneOffset.UTC));
    SettlementQuoteService service(Clock clock) { return new SettlementQuoteService(preparations, store, properties, selections, clock); }
    @BeforeEach void setup() {
        properties.setTerms(new SettlementQuote.Terms("besu-quote-v1", "SIMULATED-BESU-DEMO", new BigDecimal("2.02"), 900, new BigDecimal("1.25")));
        selections.setAllowedNetworks(Map.of(actor.customerId(), Set.of("besu-local")));
        selections.setNetworks(List.of(new SettlementNetworkRegistry.Entry(instruction.context(), true)));
        when(preparations.get(instruction.paymentId(), request.requestId(), actor)).thenReturn(view);
        when(store.find(any())).thenAnswer(c -> Optional.ofNullable(rows.get(c.getArgument(0))));
        doAnswer(c -> { var q = (SettlementQuote)c.getArgument(0); rows.put(q.review().recommendationId(), q); return null; })
                .when(store).capture(any(), anyString());
    }
    SettlementQuote prepare() { return service.prepare(instruction.paymentId(), request.requestId(), reviewId, actor); }
    @Test void serverProducesExactTokenQuoteWithoutGrantingApprovalOrRefreshingRpc() {
        var q = prepare();
        assertThat(q.availability()).isEqualTo(availability);
        assertThat(q.review().instruction()).isEqualTo(instruction);
        assertThat(q.review().feeUsd()).isEqualByComparingTo("2.02");
        assertThat(q.review().nativeFeeReserve()).isEqualByComparingTo("2e-18");
        assertThat(q.review().expiresAt()).isEqualTo(now.plusSeconds(900));
        verify(store).capture(q, actor.actorId());
        verify(preparations).get(instruction.paymentId(), request.requestId(), actor);
        verifyNoMoreInteractions(preparations);
    }
    @Test void expiredReplayReturnsOriginalTermsEvenWhenConfigurationChanges() {
        var original = prepare();
        properties.setTerms(new SettlementQuote.Terms("changed", "OTHER", BigDecimal.ONE, 1, BigDecimal.ONE));
        selections.setNetworks(List.of());
        var later = service(Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC));
        assertThat(later.prepare(instruction.paymentId(), request.requestId(), reviewId, actor)).isEqualTo(original);
        verify(store, times(1)).capture(any(), anyString());
        assertThatThrownBy(() -> original.requireCurrent(now.plusSeconds(1000))).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
    }
    @Test void customerAuthorizationHappensBeforeQuoteLookup() {
        doThrow(new IllegalStateException("denied")).when(preparations).get(anyString(), any(), any());
        assertThatThrownBy(this::prepare).hasMessage("denied");
        verifyNoInteractions(store);
    }
    @Test void missingIdentityIsRejectedBeforeAnyRead() {
        assertThatThrownBy(() -> service.prepare(instruction.paymentId(), request.requestId(), reviewId, null))
                .hasMessageContaining("Verified customer");
        verifyNoInteractions(preparations, store);
    }
    @Test void ownerScopeCannotBeTakenFromAnUnrelatedPreparation() {
        var other = new AuthorizedCustomer(UUID.randomUUID(), "foreign");
        when(preparations.get(instruction.paymentId(), request.requestId(), other)).thenReturn(view);
        assertThatThrownBy(() -> service.prepare(instruction.paymentId(), request.requestId(), reviewId, other))
                .hasMessageContaining("outside customer scope");
        verifyNoInteractions(store);
    }
    @Test void revokedNetworkPermissionPreventsNewQuote() {
        selections.setAllowedNetworks(Map.of());
        assertThatThrownBy(this::prepare).hasMessageContaining("SETTLEMENT_NETWORK_NOT_ALLOWED");
        verify(store, never()).capture(any(), anyString());
    }
    @Test void disabledNetworkCannotProduceNewQuotes() {
        selections.setNetworks(List.of(new SettlementNetworkRegistry.Entry(instruction.context(), false)));
        assertThatThrownBy(this::prepare).hasMessage("NETWORK_DISABLED");
        verify(store, never()).capture(any(), anyString());
    }
    @Test void serverTermsMustBeExplicit() {
        properties.setTerms(null);
        assertThatThrownBy(this::prepare).hasMessageContaining("SETTLEMENT_QUOTE_TERMS_REQUIRED");
        verify(store, never()).capture(any(), anyString());
    }
    @Test void expiredRpcEvidenceCannotCreateAnotherQuote() {
        var later = service(Clock.fixed(now.plusSeconds(15), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.prepare(instruction.paymentId(), request.requestId(), reviewId, actor))
                .hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
        verify(store, never()).capture(any(), anyString());
    }
    @Test void rawTotalsWithoutCanonicalAvailabilityAreInsufficient() {
        when(preparations.get(instruction.paymentId(), request.requestId(), actor)).thenReturn(
                new SettlementPreparationService.View(request, "OBSERVED_NOT_READY", "PENDING", evidence, null));
        assertThatThrownBy(this::prepare).hasMessageContaining("SETTLEMENT_AVAILABILITY_REQUIRED");
        verify(store, never()).capture(any(), anyString());
    }
    @Test void historyReadDoesNotRepriceExpiredMaterial() {
        var q = prepare();
        assertThat(service(Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC))
                .get(instruction.paymentId(), request.requestId(), reviewId, actor)).isEqualTo(q);
        verify(store, times(1)).capture(any(), anyString());
    }
    @Test void failedStorageCannotReturnAnApparentlySavedQuote() {
        doThrow(new IllegalStateException("storage-failed")).when(store).capture(any(), anyString());
        assertThatThrownBy(this::prepare).hasMessage("storage-failed");
        assertThat(rows).isEmpty();
    }

    @Test void quoteCannotBeReboundToAnotherPreparationId() {
        var original = prepare();
        var other = new SettlementPreparation.Request(UUID.randomUUID(), instruction, now);
        when(preparations.get(instruction.paymentId(), other.requestId(), actor)).thenReturn(
                new SettlementPreparationService.View(other, "PENDING", "PENDING", null, null));
        assertThatThrownBy(() -> service.prepare(instruction.paymentId(), other.requestId(), reviewId, actor))
                .hasMessageContaining("SETTLEMENT_QUOTE_KEY_CONFLICT");
        assertThat(rows.get(reviewId)).isEqualTo(original);
        verify(store, times(1)).capture(any(), anyString());
    }
    @Test void replacementChainIdentityCannotAdoptAnOldPreparation() {
        var c = instruction.context();
        var changed = new SettlementNetwork.Context(c.token(), c.nativeAsset(), address("f"), c.feeModel(), c.policyVersion());
        selections.setNetworks(List.of(new SettlementNetworkRegistry.Entry(changed, true)));
        assertThatThrownBy(this::prepare).hasMessageContaining("SETTLEMENT_CONTEXT_CHANGED");
        verify(store, never()).capture(any(), anyString());
    }
    @Test void expiredQuoteCanBeReadByOwnerButNeverByAnotherCustomer() {
        prepare(); var foreign = new AuthorizedCustomer(UUID.randomUUID(), "foreign");
        when(preparations.get(instruction.paymentId(), request.requestId(), foreign)).thenReturn(view);
        assertThatThrownBy(() -> service.get(instruction.paymentId(), request.requestId(), reviewId, foreign))
                .hasMessageContaining("outside customer scope");
    }
}
