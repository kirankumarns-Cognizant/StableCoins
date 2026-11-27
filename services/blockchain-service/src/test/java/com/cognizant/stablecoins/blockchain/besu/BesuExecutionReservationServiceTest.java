package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.blockchain.besu.BesuLiquidityStoreTest.*;

class BesuExecutionReservationServiceTest {
    final BesuRuntime runtime=mock(BesuRuntime.class);
    final SettlementExecutionReads claims=mock(SettlementExecutionReads.class);
    final SettlementPreparationReads owners=mock(SettlementPreparationReads.class);
    final BesuLiquidityStore liquidity=mock(BesuLiquidityStore.class);
    final Clock clock=mock(Clock.class);
    final BesuExecutionReservationService service=new BesuExecutionReservationService(runtime,claims,owners,liquidity,clock);
    final SettlementBinding.Execution e=execution(CONTEXT,"PAY-RESERVE","4","0.1");
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(liquidity.pinnedObservation(e)).thenReturn(Optional.empty());
        when(runtime.observe(e.instruction())).thenReturn(observation(e));
    }
    @Test void exactApprovedClaimIsCheckedBeforeRpcAndPinnedWithFreshEvidence() {
        var result=service.reserve(e);
        assertThat(result.execution()).isEqualTo(e);
        assertThat(result.observation()).isEqualTo(observation(e));
        assertThat(result.status()).isEqualTo("PINNED_NOT_SUBMITTED");
        assertThat(result.executionGate()).isEqualTo("DURABLE_SUBMISSION_INTEGRATION_PENDING");
        var order=inOrder(claims,liquidity,owners,runtime);
        order.verify(claims).requireClaimed(e);
        order.verify(liquidity).lock(CONTEXT);
        order.verify(claims).requireClaimed(e);
        order.verify(liquidity).pinnedObservation(e);
        order.verify(owners).requireInstruction(e.instruction());
        order.verify(runtime).observe(e.instruction());
        order.verify(owners).requireInstruction(e.instruction());
        order.verify(claims).requireClaimed(e);
        order.verify(liquidity).hold(e,observation(e));
        order.verify(liquidity).pin(e);
        order.verify(liquidity).capturePinnedObservation(e,observation(e));
        verifyNoMoreInteractions(runtime);
    }
    @Test void forgedClaimCannotReachLockOrRpc() {
        doThrow(new IllegalStateException("CLAIM_MISSING")).when(claims).requireClaimed(e);
        assertThatThrownBy(()->service.reserve(e)).hasMessage("CLAIM_MISSING");
        verifyNoInteractions(liquidity,runtime,owners);
    }
    @Test void claimChangedDuringLockWaitFailsBeforeRpc() {
        doNothing().doThrow(new IllegalStateException("CLAIM_CHANGED")).when(claims).requireClaimed(e);
        assertThatThrownBy(()->service.reserve(e)).hasMessage("CLAIM_CHANGED");
        verifyNoInteractions(runtime,owners);
        verify(liquidity,never()).hold(any(),any());
    }
    @Test void durableReplayReturnsOriginalEvidenceEvenAfterReviewExpiryWithoutRpcOrRenewal() {
        when(clock.instant()).thenReturn(NOW.plusSeconds(300));
        when(liquidity.pinnedObservation(e)).thenReturn(Optional.of(observation(e)));
        var result=service.reserve(e);
        assertThat(result.observation()).isEqualTo(observation(e));
        verifyNoInteractions(runtime,owners,clock);
        verify(liquidity,never()).hold(any(),any());
        verify(liquidity,never()).pin(any());
        verify(liquidity,never()).capturePinnedObservation(any(),any());
    }
    @Test void reviewExpiredWhileWaitingForTreasuryCannotReachRpc() {
        when(clock.instant()).thenReturn(e.review().expiresAt());
        assertThatThrownBy(()->service.reserve(e)).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
        verifyNoInteractions(runtime,owners);
    }
    @Test void ownerChangedDuringRpcCannotAcquireHold() {
        doNothing().doThrow(new IllegalStateException("OWNER_CHANGED")).when(owners).requireInstruction(e.instruction());
        assertThatThrownBy(()->service.reserve(e)).hasMessage("OWNER_CHANGED");
        verify(liquidity,never()).hold(any(),any());
    }
    @Test void claimChangedDuringRpcCannotAcquireHold() {
        doNothing().doNothing().doThrow(new IllegalStateException("CLAIM_CHANGED")).when(claims).requireClaimed(e);
        assertThatThrownBy(()->service.reserve(e)).hasMessage("CLAIM_CHANGED");
        verify(liquidity,never()).hold(any(),any());
    }
    @Test void rpcIdentityOrReadinessFailureCannotAcquireHold() {
        when(runtime.observe(e.instruction())).thenThrow(new IllegalStateException("BESU_GENESIS_MISMATCH"));
        assertThatThrownBy(()->service.reserve(e)).hasMessage("BESU_GENESIS_MISMATCH");
        verify(liquidity,never()).hold(any(),any());
    }
    @Test void expiredRpcObservationCannotAcquireHold() {
        when(clock.instant()).thenReturn(NOW,NOW.plusSeconds(15));
        assertThatThrownBy(()->service.reserve(e)).hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
        verify(liquidity,never()).hold(any(),any());
    }
    @Test void preparationSnapshotCannotSubstituteForNewExecutionObservation() {
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        assertThatThrownBy(()->service.reserve(e)).hasMessage("BESU_EXECUTION_OBSERVATION_PREDATES_CHECK");
        verify(liquidity,never()).hold(any(),any());
    }
    @Test void capacityOrFeeFailureCannotPin() {
        doThrow(new IllegalStateException("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT")).when(liquidity).hold(e,observation(e));
        assertThatThrownBy(()->service.reserve(e)).hasMessage("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT");
        verify(liquidity,never()).pin(any());
        verify(liquidity,never()).capturePinnedObservation(any(),any());
    }
    @Test void partialOrReleasedCommitmentIsNotSilentlyAdoptedOrRefreshed() {
        when(liquidity.pinnedObservation(e)).thenThrow(new IllegalStateException("BESU_RESERVATION_REQUIRES_RECOVERY"));
        assertThatThrownBy(()->service.reserve(e)).hasMessage("BESU_RESERVATION_REQUIRES_RECOVERY");
        verifyNoInteractions(runtime,owners);
    }
}