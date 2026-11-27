package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.blockchain.besu.BesuBlockchainAdapterTest.*;

class BesuRuntimeObservationTest {
    static final Instant NOW = CLOCK.instant();
    static SettlementBinding.Instruction instruction(SettlementNetwork.Context context, Instant deadline) {
        return new SettlementBinding.Instruction(UUID.randomUUID(), 1, UUID.randomUUID(),
                "PAY-BESU-OBSERVE", "CORR-BESU-OBSERVE", context, RECIPIENT,
                new BigDecimal("10"), new BigDecimal("4"), SettlementBinding.Outcome.TOKEN_WALLET,
                new BigDecimal("3"), deadline);
    }
    static BesuRuntime runtime(Fixture fixture, Clock clock) {
        return new BesuRuntime(fixture.adapter(), new BesuHttpRpc("http://127.0.0.1:1"), clock);
    }
    @Test void producesExactInstructionBoundLiveEvidenceWithoutSigningOrSubmission() {
        var f = new Fixture();
        var i = instruction(context(), NOW.plusSeconds(60));
        try (var r = runtime(f, CLOCK)) {
            var o = r.observe(i);
            assertThat(o.instruction()).isEqualTo(i);
            assertThat(o.observedBlock()).isEqualTo(100);
            assertThat(o.gasLimit()).isEqualByComparingTo("75000");
            assertThat(o.executionFeeEstimate()).isEqualByComparingTo("0.000000000075");
            assertThat(o.expiresAt()).isEqualTo(NOW.plusSeconds(15));
            assertThat(o.totalsCoverEstimate()).isTrue();
            o.requireFor(i, NOW);
            assertThatThrownBy(() -> r.forNewWork("besu-local")).hasMessage("NETWORK_DISABLED");
        }
        assertThat(f.signatures).isZero();
        assertThat(f.calls).doesNotContainKeys("eth_sendRawTransaction", "eth_getTransactionCount");
    }
    @Test void mismatchedGenesisOrPolicyIsRejectedBeforeAnyRpc() {
        var c = context();
        var reset = new SettlementNetwork.Chain("besu-local", BesuSettings.CHAIN_ID,
                "0x" + "d".repeat(64), SettlementNetwork.Classification.PRIVATE_DEMO);
        var changed = new SettlementNetwork.Context(new SettlementNetwork.Token(reset, CONTRACT, "DEMOUSD", 6,
                c.token().codeHash()), new SettlementNetwork.NativeAsset(reset, "BESU_GAS", 18),
                c.treasury(), c.feeModel(), c.policyVersion());
        var changedPolicy = new SettlementNetwork.Context(c.token(), c.nativeAsset(), c.treasury(), c.feeModel(), "besu-demo-v2");
        var f = new Fixture();
        try (var r = runtime(f, CLOCK)) {
            for (var context : java.util.List.of(changed, changedPolicy))
                assertThatThrownBy(() -> r.observe(instruction(context, NOW.plusSeconds(60))))
                        .hasMessage("PINNED_NETWORK_CONTEXT_CHANGED");
        }
        assertThat(f.calls).isEmpty();
    }
    @Test void expiredInstructionAndUnacceptedRecipientNeverTriggerRpc() {
        var f = new Fixture();
        try (var r = runtime(f, CLOCK)) {
            assertThatThrownBy(() -> r.observe(instruction(context(), NOW)))
                    .hasMessage("SETTLEMENT_INSTRUCTION_DEADLINE_PASSED");
            assertThatThrownBy(() -> r.observe("0x" + "9".repeat(40), BigDecimal.ONE))
                    .hasMessage("BESU_RECIPIENT_NOT_ACCEPTED");
            assertThatThrownBy(() -> r.observe(RECIPIENT, new BigDecimal("0.0000001")))
                    .hasMessage("BESU_TOKEN_AMOUNT_INVALID");
        }
        assertThat(f.calls).isEmpty();
    }
    @Test void observationLifetimeIsClippedToTheCustomerDeadline() {
        var f = new Fixture();
        try (var r = runtime(f, CLOCK)) {
            var i = instruction(context(), NOW.plusSeconds(3));
            var o = r.observe(i);
            assertThat(o.expiresAt()).isEqualTo(i.deliverBy());
            assertThatThrownBy(() -> o.requireFor(i, i.deliverBy()))
                    .hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
        }
    }
    @Test void rejectsObservationThatConsumedItsEntireFreshnessWindow() {
        var f = new Fixture();
        var time = mock(Clock.class);
        when(time.instant()).thenReturn(NOW, NOW, NOW.plusSeconds(15));
        try (var r = runtime(f, time)) {
            assertThatThrownBy(() -> r.observe(instruction(context(), NOW.plusSeconds(60))))
                    .hasMessage("BESU_OBSERVATION_WINDOW_EXPIRED");
        }
        assertThat(f.signatures).isZero();
        assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
    }
    @Test void rpcIdentityFailureCannotProduceBoundEvidenceOrAuthorizeWork() {
        var f = new Fixture();
        f.overrides.put("eth_getCode", value("0x6001"));
        try (var r = runtime(f, CLOCK)) {
            assertThatThrownBy(() -> r.observe(instruction(context(), NOW.plusSeconds(60))))
                    .hasMessage("BESU_TOKEN_CODE_MISMATCH");
        }
        assertThat(f.signatures).isZero();
        assertThat(f.calls).doesNotContainKey("eth_sendRawTransaction");
    }
}
