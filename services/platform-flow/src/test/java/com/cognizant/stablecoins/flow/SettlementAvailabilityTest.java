package com.cognizant.stablecoins.flow;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SettlementAvailabilityTest {
    static final Instant NOW=SettlementBindingTest.NOW;
    static SettlementPreparation.Evidence evidence() {
        var i=SettlementBindingTest.instruction();
        return new SettlementPreparation.Evidence(new SettlementPreparation.Request(UUID.randomUUID(),i,NOW),
                SettlementObservationTest.observation(i));
    }
    @Test void committedFundsReduceCapacityWithoutCreatingAuthority() {
        var e=evidence();
        var a=new SettlementAvailability(e,new BigDecimal("999999.5"),BigDecimal.ONE,NOW);
        assertThat(a.tokenAvailable()).isEqualByComparingTo("0.5");
        assertThat(a.nativeAvailable()).isZero();
        assertThat(a.capacitySufficient()).isFalse();
        var free=new SettlementAvailability(e,BigDecimal.ZERO,BigDecimal.ZERO,NOW);
        assertThat(free.capacitySufficient()).isTrue();
    }
    @Test void overcommitmentNeverProducesNegativeAvailableBalances() {
        var a=new SettlementAvailability(evidence(),new BigDecimal("1000001"),BigDecimal.TEN,NOW);
        assertThat(a.tokenAvailable()).isZero();assertThat(a.nativeAvailable()).isZero();
        assertThat(a.capacitySufficient()).isFalse();
    }
    @Test void cannotRefreshOldEvidenceOrUseFutureSnapshot() {
        var e=evidence();
        assertThatThrownBy(()->new SettlementAvailability(e,BigDecimal.ZERO,BigDecimal.ZERO,NOW.plusSeconds(15)))
                .hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
        var a=new SettlementAvailability(e,BigDecimal.ZERO,BigDecimal.ZERO,NOW.plusSeconds(1));
        assertThatThrownBy(()->a.requireFor(e.request(),NOW)).hasMessage("SETTLEMENT_AVAILABILITY_NOT_CURRENT");
        assertThatThrownBy(()->a.requireFor(e.request(),NOW.plusSeconds(15))).hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
    }
    @Test void strictCodecRetainsCanonicalAvailability() {
        var a=new SettlementAvailability(evidence(),new BigDecimal("2.000000"),new BigDecimal("0.1"),NOW);
        var restored=SettlementBindingJson.read(SettlementBindingJson.write(a),SettlementAvailability.class);
        assertThat(restored).isEqualTo(a);
        assertThat(SettlementBindingJson.fingerprint(restored)).isEqualTo(SettlementBindingJson.fingerprint(a));
        var extra=SettlementBindingJson.write(a).replaceFirst("\\{","{\"executionAllowed\":true,");
        assertThatThrownBy(()->SettlementBindingJson.read(extra,SettlementAvailability.class)).hasMessage("INVALID_SETTLEMENT_BINDING");
    }
    @Test void invalidPrecisionAndNegativeCommitmentsFailClosed() {
        var e=evidence();
        for(var value:new BigDecimal[]{new BigDecimal("-1"),new BigDecimal("0.0000001")})
            assertThatThrownBy(()->new SettlementAvailability(e,value,BigDecimal.ZERO,NOW)).hasMessage("INVALID_COMMITTED_AMOUNT");
        assertThatThrownBy(()->new SettlementAvailability(e,BigDecimal.ZERO,new BigDecimal("1e-19"),NOW))
                .hasMessage("INVALID_COMMITTED_AMOUNT");
    }
}
