package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuReceiptServiceTest {
    final BesuSubmissionServiceTest f=new BesuSubmissionServiceTest();
    final AtomicReference<BesuFinalReceipt> receipt=new AtomicReference<>();
    final BesuReceiptService worker=new BesuReceiptService(f.runtime,f.claims,f.liquidity,f.store,f.database);
    @BeforeEach void setup() {
        f.setup(); f.existing("SUBMITTED");
        when(f.store.receipt(any())).thenAnswer(c->Optional.ofNullable(receipt.get()));
        doAnswer(c->{
            var stored=(BesuSubmissionStore.Stored)c.getArgument(0);
            var r=(BesuFinalReceipt)c.getArgument(1);
            assertThat(f.commits.get()).isEqualTo(1); r.requireFor(stored.transfer());
            receipt.set(r); f.row.set(new BesuSubmissionStore.Stored(stored.transfer(),r.status())); return null;
        }).when(f.store).resolve(any(),any());
        when(f.adapter.finalReceipt(any())).thenAnswer(c->Optional.of(finalized(true)));
    }
    BesuFinalReceipt finalized(boolean success) {
        return new BesuFinalReceipt(f.e.executionId(),f.row.get().transfer().hash,BesuBlockchainAdapterTest.BLOCK,
                101,success,new BigDecimal("21000"),new BigDecimal("1000"),BigInteger.valueOf(21000000));
    }
    @Test void confirmsExactReceiptAndResolvesOnceWithHistoricalReplay() {
        var result=worker.confirm(f.e);
        assertThat(result.status()).isEqualTo("CONFIRMED"); assertThat(result.feeWei()).isEqualTo(BigInteger.valueOf(21000000));
        assertThat(result.receiptEvidence()).isEqualTo("QBFT_COMMITTED");
        assertThat(result.executionGate()).isEqualTo("PAYMENT_ACCOUNTING_INTEGRATION_PENDING");
        assertThat(worker.confirm(f.e)).isEqualTo(result);
        verify(f.adapter).finalReceipt(any()); verify(f.store).resolve(any(),any());
        verify(f.adapter,never()).submitRawTransaction(any()); verifyNoInteractions(f.owners);
    }
    @ParameterizedTest @ValueSource(strings={"ATTEMPTED","UNKNOWN","SUBMITTED"})
    void conclusiveRevertResolvesOriginalHashAndRetainsActualGas(String state) {
        f.existing(state); doReturn(Optional.of(finalized(false))).when(f.adapter).finalReceipt(any());
        var r=worker.confirm(f.e);
        assertThat(r.status()).isEqualTo("REVERTED"); assertThat(r.feeWei()).isEqualTo(BigInteger.valueOf(21000000));
        verify(f.store).resolve(any(),any()); verify(f.adapter,never()).submitRawTransaction(any());
    }
    @Test void absentReceiptRetainsSubmissionAndCommitment() {
        doReturn(Optional.empty()).when(f.adapter).finalReceipt(any());
        var r=worker.confirm(f.e);
        assertThat(r.status()).isEqualTo("SUBMITTED"); assertThat(r.receiptEvidence()).isEqualTo("RECEIPT_PENDING");
        verify(f.store,never()).resolve(any(),any());
    }
    @Test void invalidOrUnavailableRpcRetainsCommitmentWithoutLeakingProviderMessage() {
        doThrow(new IllegalStateException("secret RPC error")).when(f.adapter).finalReceipt(any());
        var r=worker.confirm(f.e);
        assertThat(r.receiptEvidence()).isEqualTo("RECEIPT_UNVERIFIED"); assertThat(r.toString()).doesNotContain("secret");
        verify(f.store,never()).resolve(any(),any());
    }
    @Test void unattemptedSignatureCannotBeFinalized() {
        f.existing("SIGNED"); assertThat(worker.confirm(f.e).receiptEvidence()).isEqualTo("NOT_ATTEMPTED");
        verifyNoInteractions(f.runtime); verify(f.store,never()).resolve(any(),any());
    }
    @Test void wrongExecutionReceiptCannotResolveOriginalCapacity() {
        var r=finalized(true);
        doReturn(Optional.of(new BesuFinalReceipt(UUID.randomUUID(),r.transactionHash(),r.blockHash(),r.blockNumber(),
                true,r.gasUsed(),r.gasPriceWei(),r.totalFeeWei()))).when(f.adapter).finalReceipt(any());
        assertThatThrownBy(()->worker.confirm(f.e)).hasMessage("BESU_FINAL_RECEIPT_BINDING_CONFLICT");
        verify(f.store,never()).resolve(any(),any());
    }
    @Test void resolutionFailurePropagatesForTransactionalRollback() {
        doThrow(new IllegalStateException("DATABASE_FAILURE")).when(f.store).resolve(any(),any());
        assertThatThrownBy(()->worker.confirm(f.e)).hasMessage("DATABASE_FAILURE");
        assertThat(f.row.get().status()).isEqualTo("SUBMITTED");
    }
    @Test void missingOwnerClaimCannotReadChain() {
        doThrow(new IllegalStateException("CLAIM_MISSING")).when(f.claims).requireClaimed(f.e);
        assertThatThrownBy(()->worker.confirm(f.e)).hasMessage("CLAIM_MISSING"); verifyNoInteractions(f.runtime);
    }
    @Test void missingPinnedEvidenceCannotReadChain() {
        when(f.liquidity.pinnedObservation(f.e)).thenReturn(Optional.empty());
        assertThatThrownBy(()->worker.confirm(f.e)).hasMessage("BESU_PINNED_RESERVATION_REQUIRED"); verifyNoInteractions(f.runtime);
    }
    @Test void terminalRowWithoutReceiptFailsClosed() {
        f.existing("CONFIRMED");
        assertThatThrownBy(()->worker.confirm(f.e)).hasMessage("BESU_FINAL_RECEIPT_REQUIRED"); verifyNoInteractions(f.runtime);
    }
    @Test void competingPollerCanResolveWhileRpcIsInFlight() {
        doAnswer(c->{var r=finalized(true); receipt.set(r); f.row.set(new BesuSubmissionStore.Stored(f.row.get().transfer(),r.status()));
            return Optional.of(r);}).when(f.adapter).finalReceipt(any());
        assertThat(worker.confirm(f.e).status()).isEqualTo("CONFIRMED"); verify(f.store,never()).resolve(any(),any());
    }
    @Test void ambientTransactionIsRejectedBeforeAnyWork() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(()->worker.confirm(f.e)).hasMessage("BESU_RECEIPT_OUTSIDE_TRANSACTION_REQUIRED"); }
        finally { TransactionSynchronizationManager.clear(); }
        verifyNoInteractions(f.runtime);
    }
    @Test void receiptFeeCannotExceedSignedGasOrChangePrice() {
        var t=f.row.get().transfer(); var r=finalized(true);
        var tooMuch=new BesuFinalReceipt(f.e.executionId(),t.hash,r.blockHash(),101,true,new BigDecimal("26000"),
                new BigDecimal("1000"),BigInteger.valueOf(26000000));
        assertThatThrownBy(()->tooMuch.requireFor(t)).hasMessage("BESU_FINAL_RECEIPT_BINDING_CONFLICT");
        var changed=new BesuFinalReceipt(f.e.executionId(),t.hash,r.blockHash(),101,true,new BigDecimal("21000"),
                new BigDecimal("1001"),BigInteger.valueOf(21021000));
        assertThatThrownBy(()->changed.requireFor(t)).hasMessage("BESU_FINAL_RECEIPT_BINDING_CONFLICT");
    }
    @Test void terminalSubmissionReplayCannotBroadcastAgain() {
        receipt.set(finalized(true)); f.existing("CONFIRMED");
        assertThat(f.service.submit(f.e).status()).isEqualTo("CONFIRMED"); verifyNoInteractions(f.adapter);
    }
}
