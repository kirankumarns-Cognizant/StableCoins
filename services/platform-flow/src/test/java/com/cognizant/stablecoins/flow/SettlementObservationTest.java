package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SettlementObservationTest {
    static final Instant NOW = SettlementBindingTest.NOW;
    static SettlementObservation observation(SettlementBinding.Instruction instruction) {
        return new SettlementObservation(instruction, 100, new BigDecimal("1000000"), BigDecimal.ONE,
                new BigDecimal("62500"), new BigDecimal("75000"), new BigDecimal("1000"),
                new BigDecimal("0.000000000075"), NOW, NOW.plusSeconds(15));
    }
    @Test void bindsCompleteInstructionAndKeepsSourceUsdSeparateFromTokenQuantity() {
        var i = SettlementBindingTest.instruction();
        var distinct = new SettlementBinding.Instruction(i.intentId(), i.intentVersion(), i.customerId(),
                i.paymentId(), i.correlationId(), i.context(), i.recipient(), new BigDecimal("10"),
                new BigDecimal("4"), i.outcome(), i.maximumFeeUsd(), i.deliverBy());
        var o = observation(distinct);
        o.requireFor(distinct, NOW);
        assertThat(o.instruction().sourceUsd()).isEqualByComparingTo("10");
        assertThat(o.instruction().tokenAmount()).isEqualByComparingTo("4");
        assertThat(o.totalsCoverEstimate()).isTrue();
    }
    @Test void changedOwnerIntentCannotReuseAnObservation() {
        var o = observation(SettlementBindingTest.instruction());
        assertThatThrownBy(() -> o.requireFor(SettlementBindingTest.instruction(), NOW))
                .hasMessage("SETTLEMENT_OBSERVATION_BINDING_CHANGED");
    }
    @Test void strictCodecRoundTripsAndRejectsUnknownFieldsAndScalarCoercion() throws Exception {
        var o = observation(SettlementBindingTest.instruction());
        String json = SettlementBindingJson.write(o);
        var restored = SettlementBindingJson.read(json, SettlementObservation.class);
        assertThat(restored).isEqualTo(o);
        assertThat(SettlementBindingJson.fingerprint(restored)).isEqualTo(SettlementBindingJson.fingerprint(o));
        var mapper = new ObjectMapper();
        ObjectNode unknown = (ObjectNode) mapper.readTree(json);
        unknown.put("executionAllowed", true);
        assertThatThrownBy(() -> SettlementBindingJson.read(unknown.toString(), SettlementObservation.class))
                .hasMessage("INVALID_SETTLEMENT_BINDING");
        ObjectNode coerced = (ObjectNode) mapper.readTree(json);
        coerced.put("gasPriceWei", "1000");
        assertThatThrownBy(() -> SettlementBindingJson.read(coerced.toString(), SettlementObservation.class))
                .hasMessage("INVALID_SETTLEMENT_BINDING");
    }
    @Test void observationExpiresAtBoundaryAndRejectsFutureEvidence() {
        var o = observation(SettlementBindingTest.instruction());
        for (Instant now : List.of(NOW.minusNanos(1), NOW.plusSeconds(15), NOW.plusSeconds(16)))
            assertThatThrownBy(() -> o.requireFor(o.instruction(), now))
                    .hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
        o.requireFor(o.instruction(), NOW.plusSeconds(14));
    }
    @Test void rejectsTamperedGasFeesBalancesAndLifetime() throws Exception {
        var mapper = new ObjectMapper();
        String original = SettlementBindingJson.write(observation(SettlementBindingTest.instruction()));
        for (String field : List.of("observedBlock", "tokenTotal", "nativeTotal", "gasLimit",
                "estimatedGas", "gasPriceWei", "executionFeeEstimate", "expiresAt")) {
            ObjectNode changed = (ObjectNode) mapper.readTree(original);
            switch (field) {
                case "expiresAt" -> changed.put(field, NOW.plusSeconds(16).toString());
                case "gasLimit" -> changed.put(field, 62499);
                case "estimatedGas", "observedBlock" -> changed.put(field, 0);
                case "executionFeeEstimate" -> changed.put(field, new BigDecimal("0.000000000076"));
                case "gasPriceWei" -> changed.put(field, new BigDecimal("1000.1"));
                default -> changed.put(field, -1);
            }
            assertThatThrownBy(() -> SettlementBindingJson.read(changed.toString(), SettlementObservation.class))
                    .as(field).hasMessage("INVALID_SETTLEMENT_BINDING");
        }
    }
    @Test void insufficientTotalsAreReportedWithoutInventingAvailableBalances() {
        var i = SettlementBindingTest.instruction();
        var tokenShort = new SettlementObservation(i, 1, BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.000000000000000001"),
                NOW, NOW.plusSeconds(1));
        assertThat(tokenShort.totalsCoverEstimate()).isFalse();
        var gasShort = new SettlementObservation(i, 1, i.tokenAmount(), BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.000000000000000001"),
                NOW, NOW.plusSeconds(1));
        assertThat(gasShort.totalsCoverEstimate()).isFalse();
    }
    @Test void completeZeroFeeEvidenceIsValidForAConfiguredExecutionOnlyNetwork() {
        var i = SettlementBindingTest.instruction();
        var o = new SettlementObservation(i, 1, i.tokenAmount(), BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                NOW, NOW.plusSeconds(1));
        assertThat(o.totalsCoverEstimate()).isTrue();
    }
    @Test void cannotPresentExecutionOnlyEstimateAsCompleteBaseFeeEvidence() {
        var chain = new SettlementNetwork.Chain("base-sepolia", 84532,
                SettlementBindingTest.hash("a"), SettlementNetwork.Classification.PUBLIC_TESTNET);
        var context = new SettlementNetwork.Context(new SettlementNetwork.Token(chain,
                SettlementBindingTest.address("b"), "USDC", 6, SettlementBindingTest.hash("c")),
                new SettlementNetwork.NativeAsset(chain, "ETH", 18), SettlementBindingTest.address("d"),
                SettlementNetwork.FeeModel.BASE_L1_AND_EXECUTION, "base-v1");
        var i = SettlementBindingTest.instruction();
        var publicIntent = new SettlementBinding.Instruction(i.intentId(), 1, i.customerId(), i.paymentId(),
                i.correlationId(), context, i.recipient(), i.sourceUsd(), i.tokenAmount(),
                SettlementBinding.Outcome.USDC_WALLET, i.maximumFeeUsd(), i.deliverBy());
        assertThatThrownBy(() -> observation(publicIntent))
                .hasMessage("OBSERVATION_REQUIRES_EXECUTION_ONLY_FEE_MODEL");
    }
}
