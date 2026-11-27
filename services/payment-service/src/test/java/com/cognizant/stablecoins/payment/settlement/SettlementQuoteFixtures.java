package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class SettlementQuoteFixtures {
    private SettlementQuoteFixtures() {}
    static SettlementQuote quote(SettlementBinding.Instruction i, UUID requestId, UUID reviewId) {
        Instant now = Instant.parse("2026-11-25T10:00:00Z");
        var r = new SettlementPreparation.Request(requestId, i, now);
        var o = new SettlementObservation(i, 100, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1e-18"), now, now.plusSeconds(15));
        var a = new SettlementAvailability(new SettlementPreparation.Evidence(r, o), BigDecimal.ZERO, BigDecimal.ZERO, now);
        var t = new SettlementQuote.Terms("besu-quote-v1", "SIMULATED-BESU-DEMO", BigDecimal.ONE, 900, BigDecimal.ONE);
        return new SettlementQuote(new SettlementBinding.Review(reviewId, 1, requestId, UUID.randomUUID(), 1,
                t.providerId(), i, t.simulatedFeeUsd(), t.reserve(o), now, t.expiry(i, now)), a, t);
    }
}
