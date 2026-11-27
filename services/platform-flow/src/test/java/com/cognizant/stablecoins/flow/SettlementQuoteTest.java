package com.cognizant.stablecoins.flow;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SettlementQuoteTest {
    static final Instant NOW = SettlementBindingTest.NOW;
    static SettlementQuote.Terms terms() {
        return new SettlementQuote.Terms("besu-quote-v1", "SIMULATED-BESU-DEMO",
                new BigDecimal("2.02"), 900, new BigDecimal("1.25"));
    }
    static SettlementQuote quote(SettlementAvailability a, SettlementQuote.Terms t) {
        var request = a.preparation().request();
        var i = request.instruction();
        return new SettlementQuote(new SettlementBinding.Review(UUID.randomUUID(), 1, request.requestId(),
                UUID.randomUUID(), 1, t.providerId(), i, t.simulatedFeeUsd(),
                t.reserve(a.preparation().observation()), NOW, t.expiry(i, NOW)), a, t);
    }
    static SettlementAvailability available() {
        return new SettlementAvailability(SettlementAvailabilityTest.evidence(), BigDecimal.ZERO, BigDecimal.ZERO, NOW);
    }

    @Test void tokenAndGasRemainDifferentAssetsAndQuoteMayOutliveRpcSnapshot() {
        var q = quote(available(), terms());
        assertThat(q.review().instruction().context().token().symbol()).isEqualTo("DEMOUSD");
        assertThat(q.review().instruction().context().nativeAsset().symbol()).isEqualTo("BESU_GAS");
        assertThat(q.review().feeUsd()).isEqualByComparingTo("2.02");
        q.requireCurrent(NOW.plusSeconds(30));
        assertThatThrownBy(() -> q.availability().requireFor(q.availability().preparation().request(), NOW.plusSeconds(30)))
                .hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
        assertThatThrownBy(() -> q.requireCurrent(NOW.plusSeconds(900))).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
    }
    @Test void strictRoundTripRetainsEvidencePolicyAndCanonicalAmounts() {
        var q = quote(available(), terms());
        var copy = SettlementBindingJson.read(SettlementBindingJson.write(q), SettlementQuote.class);
        assertThat(copy).isEqualTo(q);
        assertThat(SettlementBindingJson.fingerprint(copy)).isEqualTo(SettlementBindingJson.fingerprint(q));
        var extra = SettlementBindingJson.write(q).replaceFirst("\\{", "{\"approved\":true,");
        assertThatThrownBy(() -> SettlementBindingJson.read(extra, SettlementQuote.class)).hasMessage("INVALID_SETTLEMENT_BINDING");
    }
    @Test void staleOriginalEvidenceCannotCreateANewQuote() {
        var a = available(); var t = terms(); var i = a.preparation().request().instruction();
        var r = new SettlementBinding.Review(UUID.randomUUID(), 1, a.preparation().request().requestId(),
                UUID.randomUUID(), 1, t.providerId(), i, t.simulatedFeeUsd(), t.reserve(a.preparation().observation()),
                NOW.plusSeconds(15), t.expiry(i, NOW.plusSeconds(15)));
        assertThatThrownBy(() -> new SettlementQuote(r, a, t)).hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
    }
    @Test void differentPreparationCannotReuseEvidence() {
        var q = quote(available(), terms()); var r = q.review();
        var changed = new SettlementBinding.Review(r.recommendationId(), 1, UUID.randomUUID(), r.quoteId(), 1,
                r.providerId(), r.instruction(), r.feeUsd(), r.nativeFeeReserve(), r.assessedAt(), r.expiresAt());
        assertThatThrownBy(() -> new SettlementQuote(changed, q.availability(), q.terms()))
                .hasMessage("SETTLEMENT_QUOTE_BINDING_CHANGED");
    }
    @Test void quotedUsdFeeCannotExceedCustomerLimit() {
        var t = new SettlementQuote.Terms("v1", "SIMULATED-BESU-DEMO", new BigDecimal("3.01"), 900, BigDecimal.ONE);
        assertThatThrownBy(() -> quote(available(), t)).hasMessage("CUSTOMER_FEE_LIMIT_EXCEEDED");
    }
    @Test void reserveRoundsUpToNativeSmallestUnitAndZeroFeesRemainZero() {
        var t = terms(); var i = SettlementBindingTest.instruction();
        var o = new SettlementObservation(i, 100, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1e-18"), NOW, NOW.plusSeconds(15));
        assertThat(t.reserve(o)).isEqualByComparingTo("2e-18");
        var zero = new SettlementObservation(i, 100, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, NOW, NOW.plusSeconds(15));
        var a = new SettlementAvailability(new SettlementPreparation.Evidence(
                new SettlementPreparation.Request(UUID.randomUUID(), i, NOW), zero), BigDecimal.ZERO, BigDecimal.ZERO, NOW);
        assertThat(quote(a, t).review().nativeFeeReserve()).isZero();
    }
    @Test void capacityIncludesAllCommitmentsAndTheBufferedReserve() {
        var a = available();
        var shortToken = new SettlementAvailability(a.preparation(), new BigDecimal("999999"), BigDecimal.ZERO, NOW);
        assertThatThrownBy(() -> quote(shortToken, terms())).hasMessage("SETTLEMENT_QUOTE_CAPACITY_INSUFFICIENT");
        var gas = a.preparation().observation().executionFeeEstimate();
        var shortGas = new SettlementAvailability(a.preparation(), BigDecimal.ZERO, BigDecimal.ONE.subtract(gas), NOW);
        assertThat(shortGas.capacitySufficient()).isTrue();
        assertThatThrownBy(() -> quote(shortGas, terms())).hasMessage("SETTLEMENT_QUOTE_CAPACITY_INSUFFICIENT");
    }
    @Test void quoteExpiryCannotGoBeyondInstructionDeadline() {
        var t = terms(); var i = SettlementBindingTest.instruction();
        assertThat(t.expiry(i, i.deliverBy().minusSeconds(1))).isEqualTo(i.deliverBy());
    }
    @Test void malformedPolicyCannotInventNegativeCostsOrUnboundedValidity() {
        for (int seconds : new int[]{0, 901})
            assertThatThrownBy(() -> new SettlementQuote.Terms("v1", "P", BigDecimal.ZERO, seconds, BigDecimal.ONE))
                    .hasMessage("INVALID_SETTLEMENT_QUOTE_TERMS");
        for (String multiplier : new String[]{"0.99", "10.01", "1.00001"})
            assertThatThrownBy(() -> new SettlementQuote.Terms("v1", "P", BigDecimal.ZERO, 60, new BigDecimal(multiplier)))
                    .hasMessage("INVALID_SETTLEMENT_QUOTE_TERMS");
        assertThatThrownBy(() -> new SettlementQuote.Terms("v1", "P", new BigDecimal("-1"), 60, BigDecimal.ONE))
                .hasMessage("INVALID_SETTLEMENT_QUOTE_TERMS");
    }
}
