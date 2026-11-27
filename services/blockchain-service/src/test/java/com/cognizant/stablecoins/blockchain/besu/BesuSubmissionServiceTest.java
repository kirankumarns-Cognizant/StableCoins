package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.web3j.crypto.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.blockchain.besu.BesuLiquidityStoreTest.*;

class BesuSubmissionServiceTest {
    final BesuRuntime runtime=mock(BesuRuntime.class);
    final BesuBlockchainAdapter adapter=mock(BesuBlockchainAdapter.class);
    final SettlementExecutionReads claims=mock(SettlementExecutionReads.class);
    final SettlementPreparationReads owners=mock(SettlementPreparationReads.class);
    final SettlementFundingReads funding=mock(SettlementFundingReads.class);
    final BesuLiquidityStore liquidity=mock(BesuLiquidityStore.class);
    final BesuSubmissionStore store=mock(BesuSubmissionStore.class);
    final Clock clock=mock(Clock.class);
    final AtomicReference<BesuSubmissionStore.Stored> row=new AtomicReference<>();
    final AtomicInteger commits=new AtomicInteger();
    final TransactionOperations database=new TransactionOperations() {
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> callback) {
            T result=callback.doInTransaction(new SimpleTransactionStatus()); commits.incrementAndGet(); return result;
        }
    };
    final Credentials wallet=Credentials.create("2"); // disposable fixture only
    final SettlementBinding.Execution e=execution(BesuBlockchainAdapterTest.context(),"PAY-SUBMISSION","1","0.1");
    final BesuSubmissionService service=new BesuSubmissionService(runtime,claims,owners,funding,liquidity,store,database,clock);
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(runtime.forPinnedExecution(e.context())).thenReturn(adapter);
        when(runtime.observe(e.instruction())).thenReturn(observation(e));
        when(liquidity.pinnedObservation(e)).thenReturn(Optional.of(observation(e)));
        when(store.find(e)).thenAnswer(c->Optional.ofNullable(row.get()));
        doAnswer(c->{row.set(new BesuSubmissionStore.Stored(c.getArgument(0),"SIGNED"));return null;}).when(store).save(any());
        doAnswer(c->{
            assertThat(row.get().status()).isEqualTo(c.getArgument(1));
            row.set(new BesuSubmissionStore.Stored(row.get().transfer(),c.getArgument(2)));return null;
        }).when(store).transition(any(),any(),any());
        when(adapter.signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any()))
                .thenAnswer(c->signed(c.getArgument(0),c.getArgument(5),c.getArgument(6)));
        when(adapter.submitRawTransaction(any())).thenAnswer(c->{
            assertThat(commits.get()).isEqualTo(2);
            assertThat(row.get().status()).isEqualTo("ATTEMPTED");
            return row.get().transfer().hash;
        });
    }
    byte[] signed(long nonce,BigDecimal gas,BigDecimal price) {
        String data="0xa9059cbb"+"0".repeat(24)+e.instruction().recipient().substring(2)
                +org.web3j.utils.Numeric.toHexStringNoPrefixZeroPadded(BigInteger.valueOf(1_000_000),64);
        return TransactionEncoder.signMessage(RawTransaction.createTransaction(BigInteger.valueOf(nonce),price.toBigIntegerExact(),
                gas.toBigIntegerExact(),e.context().token().contract(),BigInteger.ZERO,data),BesuSettings.CHAIN_ID,wallet);
    }
    void existing(String status) {
        row.set(new BesuSubmissionStore.Stored(new BesuSignedTransfer(e,0,new BigDecimal("25000"),
                new BigDecimal("1000"),signed(0,new BigDecimal("25000"),new BigDecimal("1000"))),status));
    }
    @Test void absentLedgerAllocationRejectsBeforeRpcOrSigning() {
        doThrow(new IllegalStateException("POSTED_BESU_ALLOCATION_REQUIRED")).when(funding).requireAllocated(e);
        assertThatThrownBy(()->service.submit(e)).hasMessage("POSTED_BESU_ALLOCATION_REQUIRED");
        verifyNoInteractions(adapter);verify(runtime,never()).observe(any());assertThat(row.get()).isNull();
    }
    @Test void fundedOwnerStateIsRequiredBeforeNewWork() {
        doThrow(new IllegalStateException("SETTLEMENT_OWNER_INSTRUCTION_CHANGED")).when(owners).requireFundedInstruction(e.instruction());
        assertThatThrownBy(()->service.submit(e)).hasMessage("SETTLEMENT_OWNER_INSTRUCTION_CHANGED");
        verifyNoInteractions(adapter,funding);
    }
    @Test void signedButUnattemptedPayloadStillRequiresAllocation() {
        existing("SIGNED");doThrow(new IllegalStateException("POSTED_BESU_ALLOCATION_REQUIRED")).when(funding).requireAllocated(e);
        assertThatThrownBy(()->service.submit(e)).hasMessage("POSTED_BESU_ALLOCATION_REQUIRED");
        assertThat(row.get().status()).isEqualTo("SIGNED");verifyNoInteractions(adapter);
    }
    @Test void attemptedRecoveryDoesNotRequireNewFundingAuthority() {
        existing("UNKNOWN");when(adapter.observesTransaction(any())).thenReturn(true);
        assertThat(service.submit(e).status()).isEqualTo("SUBMITTED");verifyNoInteractions(owners,funding);
        verify(adapter,never()).submitRawTransaction(any());
    }
    @Test void commitsExactSignatureAndAttemptBeforeSingleBroadcast() {
        var result=service.submit(e);
        assertThat(result.status()).isEqualTo("SUBMITTED"); assertThat(result.transactionHash()).isEqualTo(row.get().transfer().hash);
        assertThat(result.executionGate()).isEqualTo("RECEIPT_WORKFLOW_INTEGRATION_PENDING");
        verify(adapter).signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any());
        verify(adapter).submitRawTransaction(any());
        verify(liquidity,times(2)).requireSigningCapacity(eq(e),any());
    }
    @Test void timeoutKeepsExactHashAndPinnedFundsWithoutAutomaticRetry() {
        doThrow(new IllegalStateException("secret provider error")).when(adapter).submitRawTransaction(any());
        var first=service.submit(e); var replay=service.submit(e);
        assertThat(first.status()).isEqualTo("UNKNOWN"); assertThat(replay).isEqualTo(first);
        verify(adapter).submitRawTransaction(any());
        verify(adapter).signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any());
        verify(liquidity,never()).releaseUnsigned(any());
    }
    @Test void restartAfterAttemptRecoversPresenceWithoutAnotherBroadcastOrSignature() {
        existing("ATTEMPTED"); when(adapter.observesTransaction(any())).thenReturn(true);
        assertThat(service.submit(e).status()).isEqualTo("SUBMITTED");
        verify(adapter,never()).submitRawTransaction(any());
        verify(adapter,never()).signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any());
        verifyNoInteractions(owners); verify(runtime,never()).observe(any());
    }
    @Test void absentTransactionAfterAttemptStaysUnknown() {
        existing("ATTEMPTED"); assertThat(service.submit(e).status()).isEqualTo("UNKNOWN");
        verify(adapter,never()).submitRawTransaction(any());
    }
    @Test void readFailureAfterAttemptStaysUnknownAndDoesNotExposeProviderError() {
        existing("ATTEMPTED"); when(adapter.observesTransaction(any())).thenThrow(new IllegalStateException("secret"));
        assertThat(service.submit(e).status()).isEqualTo("UNKNOWN");
    }
    @Test void previouslyAcknowledgedSubmissionCannotBeDowngradedByMissingRpcResult() {
        existing("SUBMITTED"); assertThat(service.submit(e).status()).isEqualTo("SUBMITTED");
        verify(store,never()).transition(any(),any(),any());
    }
    @Test void unknownTransactionCanRecoverAfterOriginalQuoteExpiry() {
        existing("UNKNOWN"); when(clock.instant()).thenReturn(NOW.plusSeconds(600));
        when(adapter.observesTransaction(any())).thenReturn(true);
        assertThat(service.submit(e).status()).isEqualTo("SUBMITTED");
        verifyNoInteractions(owners);
    }
    @Test void restartBeforeAttemptUsesOriginalSignatureWithFreshChecks() {
        existing("SIGNED"); service.submit(e);
        verify(adapter,never()).signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any());
        verify(adapter).submitRawTransaction(any()); verify(runtime).observe(e.instruction());
    }
    @Test void expiryBeforeFirstAttemptCannotBroadcastSavedSignature() {
        existing("SIGNED"); when(clock.instant()).thenReturn(e.review().expiresAt());
        assertThatThrownBy(()->service.submit(e)).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
        verify(adapter,never()).submitRawTransaction(any());
    }
    @Test void unsignedExpiredReviewCannotSign() {
        when(clock.instant()).thenReturn(e.review().expiresAt());
        assertThatThrownBy(()->service.submit(e)).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
        verifyNoInteractions(adapter);
    }
    @Test void missingClaimCannotReachRpcOrStorage() {
        doThrow(new IllegalStateException("CLAIM_MISSING")).when(claims).requireClaimed(e);
        assertThatThrownBy(()->service.submit(e)).hasMessage("CLAIM_MISSING"); verifyNoInteractions(runtime,store);
    }
    @Test void missingPinnedReservationCannotReachRpcOrSigner() {
        when(liquidity.pinnedObservation(e)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.submit(e)).hasMessage("BESU_PINNED_RESERVATION_REQUIRED"); verifyNoInteractions(runtime,adapter);
    }
    @Test void insufficientSharedCapacityCannotSign() {
        doThrow(new IllegalStateException("CAPACITY")).when(liquidity).requireSigningCapacity(any(),any());
        assertThatThrownBy(()->service.submit(e)).hasMessage("CAPACITY"); verifyNoInteractions(adapter);
    }
    @Test void priorUnresolvedTreasurySubmissionBlocksNewNonce() {
        doThrow(new IllegalStateException("UNRESOLVED")).when(store).requireNoOtherSubmission(e);
        assertThatThrownBy(()->service.submit(e)).hasMessage("UNRESOLVED"); verifyNoInteractions(adapter);
    }
    @Test void failedSignaturePersistenceCannotBroadcast() {
        doThrow(new IllegalStateException("DATABASE")).when(store).save(any());
        assertThatThrownBy(()->service.submit(e)).hasMessage("DATABASE"); verify(adapter,never()).submitRawTransaction(any());
    }
    @Test void failedAttemptCommitCannotBroadcast() {
        doThrow(new IllegalStateException("DATABASE")).when(store).transition(e,"SIGNED","ATTEMPTED");
        assertThatThrownBy(()->service.submit(e)).hasMessage("DATABASE"); verify(adapter,never()).submitRawTransaction(any());
    }
    @Test void differentBroadcastHashRemainsUnknown() {
        doReturn("0x"+"f".repeat(64)).when(adapter).submitRawTransaction(any());
        assertThat(service.submit(e).status()).isEqualTo("UNKNOWN");
    }
    @Test void invalidNonceCannotSign() {
        when(adapter.getPendingNonce(anyString())).thenReturn(Long.MAX_VALUE);
        assertThatThrownBy(()->service.submit(e)).hasMessage("BESU_NONCE_INVALID");
        verify(adapter,never()).signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any());
    }
    @Test void signedPayloadMustMatchExactExecutionAndIsDefensivelyCopied() {
        var bytes=signed(0,new BigDecimal("25000"),new BigDecimal("1000"));
        var value=new BesuSignedTransfer(e,0,new BigDecimal("25000"),new BigDecimal("1000"),bytes);
        Arrays.fill(bytes,(byte)0); var copy=value.payload(); Arrays.fill(copy,(byte)0);
        assertThat(value.payload()[0]).isNotZero();
        assertThat(value.toString()).doesNotContain(org.web3j.utils.Numeric.toHexString(value.payload()));
        assertThatThrownBy(()->new BesuSignedTransfer(e,1,value.gasLimit,value.gasPrice,value.payload()))
                .hasMessage("BESU_SIGNED_EXECUTION_MISMATCH");
    }
    @Test void ambientTransactionIsRejectedBeforeAnyWork() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(()->service.submit(e)).hasMessage("BESU_SUBMISSION_OUTSIDE_TRANSACTION_REQUIRED"); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(claims,liquidity,runtime,store);
    }
    @Test void expiryDuringSigningCannotPersistOrBroadcast() {
        doAnswer(c->{when(clock.instant()).thenReturn(e.review().expiresAt());
            return signed(c.getArgument(0),c.getArgument(5),c.getArgument(6));})
                .when(adapter).signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any());
        assertThatThrownBy(()->service.submit(e)).hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
        verify(store,never()).save(any()); verify(adapter,never()).submitRawTransaction(any());
    }
    @Test void savedSignatureCannotBeRepricedWhenFeesGrowBeforeAttempt() {
        existing("SIGNED"); var o=observation(e);
        when(runtime.observe(e.instruction())).thenReturn(new SettlementObservation(e.instruction(),o.observedBlock(),
                o.tokenTotal(),o.nativeTotal(),o.estimatedGas(),o.gasLimit(),new BigDecimal("2000"),
                o.executionFeeEstimate().multiply(new BigDecimal("2")),o.observedAt(),o.expiresAt()));
        assertThatThrownBy(()->service.submit(e)).hasMessage("BESU_SIGNED_FEE_REVIEW_REQUIRED");
        verify(store,never()).transition(any(),any(),any()); verify(adapter,never()).submitRawTransaction(any());
    }
    @Test void signerReturningAnotherNonceCannotPersistOrBroadcast() {
        doAnswer(c->signed(1,c.getArgument(5),c.getArgument(6)))
                .when(adapter).signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any());
        assertThatThrownBy(()->service.submit(e)).hasMessage("BESU_SIGNED_EXECUTION_MISMATCH");
        verify(store,never()).save(any()); verify(adapter,never()).submitRawTransaction(any());
    }
    @Test void observationOfUnattemptedSignatureNeverStartsAnAttemptOrLoadsFreshAuthority() {
        existing("SIGNED");assertThat(service.observeSubmission(e).status()).isEqualTo("SIGNED");
        verifyNoInteractions(runtime,adapter,funding,owners);verify(store,never()).transition(any(),any(),any());
    }
    @Test void observationCannotCreateASignatureForMissingStorage() {
        assertThatThrownBy(()->service.observeSubmission(e)).hasMessage("BESU_SIGNED_SUBMISSION_REQUIRED");
        verifyNoInteractions(runtime,adapter,funding,owners);verify(store,never()).save(any());
    }
    @Test void unknownObservationNeverSignsOrBroadcastsEvenWithExpiredReview() {
        existing("UNKNOWN");when(clock.instant()).thenReturn(e.review().expiresAt().plusSeconds(100));
        when(adapter.observesTransaction(any())).thenReturn(true);
        assertThat(service.observeSubmission(e).status()).isEqualTo("SUBMITTED");
        verify(adapter).observesTransaction(any());verify(adapter,never()).submitRawTransaction(any());
        verify(adapter,never()).getPendingNonce(anyString());verifyNoInteractions(funding,owners);
    }
    @Test void observationRejectsAnAmbientTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {assertThatThrownBy(()->service.observeSubmission(e)).hasMessage("BESU_SUBMISSION_OUTSIDE_TRANSACTION_REQUIRED");}
        finally {TransactionSynchronizationManager.setActualTransactionActive(false);}
        verifyNoInteractions(claims,liquidity,store,runtime);
    }
}