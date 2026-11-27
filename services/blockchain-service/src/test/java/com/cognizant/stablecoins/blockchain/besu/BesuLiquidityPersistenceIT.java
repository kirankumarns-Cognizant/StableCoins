package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.blockchain.besu.BesuLiquidityStoreTest.*;

/** Real PostgreSQL locking/rollback and V7/V8/V9/V10/V11 constraints. Owner claim projections are storage fixtures; no chain or approval authority acceptance. */
@Testcontainers
class BesuLiquidityPersistenceIT {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    static BesuLiquidityStore store;
    @BeforeAll static void schema() throws Exception {
        var ds=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        store=new BesuLiquidityStore(jdbc,CLOCK);
        jdbc.execute("create table payment_settlement_execution_claim(execution_id uuid primary key,payment_id text,correlation_id text,customer_id uuid,instruction_id uuid,recommendation_id uuid,recommendation_version int,quote_id uuid,quote_version int,preparation_id uuid,claimed_at timestamptz,execution_document jsonb,execution_fingerprint text)");
        // Read-discovery fixtures only; real payment migrations/cross-owner acceptance remain separate.
        jdbc.execute("create table payment(payment_id text primary key,status text)");
        jdbc.execute("create table payment_besu_funding(execution_id uuid primary key,payment_id text,execution_document jsonb,execution_fingerprint text)");
        for(var name:List.of("V7__besu_preparation_observation.sql","V8__besu_liquidity_commitments.sql","V9__besu_execution_reservation.sql","V10__besu_durable_submission.sql","V11__besu_final_receipt.sql"))
            try(var in=new ClassPathResource("db/migration/"+name).getInputStream()) {
                jdbc.execute(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            }
    }
    static SettlementBinding.Execution seed(SettlementNetwork.Context context,String payment,String amount,String fee) {
        var e=execution(context,payment,amount,fee);
        var r=e.review(); var i=e.instruction();
        jdbc.update("insert into payment_settlement_execution_claim values (?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?)",
                e.executionId(),i.paymentId(),i.correlationId(),i.customerId(),i.intentId(),r.recommendationId(),
                r.version(),r.quoteId(),r.quoteVersion(),r.preparationId(),java.sql.Timestamp.from(NOW),
                SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));
        return e;
    }
    @Test void concurrentDifferentTokensCompeteForTheSameNativeGasPool() throws Exception {
        var ctx=context(CHAIN,"b","1");
        var a=seed(ctx,"PAY-CONCURRENT-A","1","0.6");
        var b=seed(context(CHAIN,"f","1"),"PAY-CONCURRENT-B","1","0.6");
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            java.util.function.Function<SettlementBinding.Execution,Callable<Boolean>> reserve=e->()->{
                start.await();
                try {tx.executeWithoutResult(s->store.hold(e,observation(e)));return true;}
                catch(IllegalStateException ex) {
                    assertThat(ex).hasMessage("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT");return false;
                }
            };
            var first=pool.submit(reserve.apply(a));var second=pool.submit(reserve.apply(b));start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("select count(*) from besu_liquidity_commitment where scope_key=?",
                    Integer.class,ctx.nonceScope())).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void aFailedOwnerTransactionRollsBackTheHold() {
        var e=seed(context(CHAIN,"b","2"),"PAY-ROLLBACK","1","0.1");
        assertThatThrownBy(()->tx.executeWithoutResult(s->{
            store.hold(e,observation(e));throw new IllegalStateException("owner-failed");
        })).hasMessage("owner-failed");
        assertThat(jdbc.queryForObject("select count(*) from besu_liquidity_commitment where execution_id=?",
                Integer.class,e.executionId())).isZero();
    }
    @Test void immutableTermsAndPinnedStateCannotBeRewrittenOrDeleted() {
        var e=seed(context(CHAIN,"b","3"),"PAY-IMMUTABLE","1","0.1");
        tx.executeWithoutResult(s->{store.hold(e,observation(e));store.pin(e);});
        for(var sql:List.of("delete from besu_liquidity_commitment where execution_id=?",
                "update besu_liquidity_commitment set status='RELEASED',pinned_at=null where execution_id=?",
                "update besu_liquidity_commitment set expires_at=expires_at+interval '1 hour' where execution_id=?"))
            assertThatThrownBy(()->jdbc.update(sql,e.executionId())).hasMessageContaining("BESU_COMMITMENT_IMMUTABLE");
        var later=new BesuLiquidityStore(jdbc,Clock.offset(CLOCK,Duration.ofSeconds(120)));
        var next=seed(e.context(),"PAY-PINNED-OBS","1","0.1");
        var at=NOW.plusSeconds(120);
        var old=observation(next);
        var fresh=new SettlementObservation(next.instruction(),101,old.tokenTotal(),old.nativeTotal(),
                old.estimatedGas(),old.gasLimit(),old.gasPriceWei(),old.executionFeeEstimate(),at,at.plusSeconds(15));
        var evidence=new SettlementPreparation.Evidence(new SettlementPreparation.Request(
                java.util.UUID.randomUUID(),next.instruction(),at),fresh);
        var snapshot=tx.execute(s->later.snapshot(evidence));
        assertThat(snapshot.tokenCommitted()).isEqualByComparingTo("1");
        assertThat(snapshot.nativeCommitted()).isEqualByComparingTo("0.1");
    }
    @Test void availabilityEvidenceIsAppendOnlyAndRoundTripsJsonb() {
        var e=seed(context(CHAIN,"b","4"),"PAY-SNAPSHOT","1","0.1");
        var ev=evidence(e);
        var a=tx.execute(s->store.snapshot(ev));
        jdbc.update("insert into besu_preparation_observation(request_id,request_fingerprint,document,fingerprint) values (?,?,cast(? as jsonb),?)",
                ev.request().requestId(),SettlementBindingJson.fingerprint(ev.request()),SettlementBindingJson.write(ev),SettlementBindingJson.fingerprint(ev));
        jdbc.update("insert into besu_preparation_availability(request_id,document,fingerprint) values (?,cast(? as jsonb),?)",
                ev.request().requestId(),SettlementBindingJson.write(a),SettlementBindingJson.fingerprint(a));
        var restored=SettlementBindingJson.read(jdbc.queryForObject(
                "select document::text from besu_preparation_availability where request_id=?",String.class,ev.request().requestId()),SettlementAvailability.class);
        assertThat(restored).isEqualTo(a);
        assertThatThrownBy(()->jdbc.update("delete from besu_preparation_availability where request_id=?",ev.request().requestId()))
                .hasMessageContaining("BESU_OBSERVATION_IMMUTABLE");
    }
    static BesuExecutionReservationService worker(BesuRuntime runtime,Clock clock) {
        return new BesuExecutionReservationService(runtime,new SettlementExecutionReads(jdbc),
                mock(SettlementPreparationReads.class),new BesuLiquidityStore(jdbc,clock),clock);
    }
    static BesuRuntime rpcFor(SettlementBinding.Execution... executions) {
        var runtime=mock(BesuRuntime.class);
        for(var e:executions) when(runtime.observe(e.instruction())).thenReturn(observation(e));
        return runtime;
    }
    static int commitments(SettlementBinding.Execution e) {
        return jdbc.queryForObject("select count(*) from besu_liquidity_commitment where execution_id=?",Integer.class,e.executionId());
    }
    @Test void workerPinsAndReplaysExactJsonbEvidenceAfterExpiryWithoutAnotherRpc() {
        var e=seed(context(CHAIN,"b","5"),"PAY-WORKER-REPLAY","4","0.1");
        var runtime=rpcFor(e);
        var first=tx.execute(s->worker(runtime,CLOCK).reserve(e));
        var later=Clock.offset(CLOCK,Duration.ofSeconds(120));
        var replay=tx.execute(s->worker(runtime,later).reserve(e));
        assertThat(replay).isEqualTo(first);
        assertThat(replay.status()).isEqualTo("PINNED_NOT_SUBMITTED");
        verify(runtime).observe(e.instruction());
        assertThat(commitments(e)).isOne();
        assertThat(jdbc.queryForObject("select status from besu_liquidity_commitment where execution_id=?",String.class,e.executionId())).isEqualTo("PINNED");
        assertThatThrownBy(()->jdbc.update("delete from besu_execution_reservation where execution_id=?",e.executionId()))
                .hasMessageContaining("BESU_OBSERVATION_IMMUTABLE");
    }
    @Test void readinessStorageFailureRollsBackBothHoldAndPin() {
        var e=seed(context(CHAIN,"b","6"),"PAY-WORKER-ROLLBACK","4","0.1");
        var runtime=rpcFor(e);
        jdbc.execute("alter table besu_execution_reservation add constraint reservation_test_failure check(false) not valid");
        try {
            assertThatThrownBy(()->tx.execute(s->worker(runtime,CLOCK).reserve(e)))
                    .hasMessageContaining("reservation_test_failure");
            assertThat(commitments(e)).isZero();
        } finally {jdbc.execute("alter table besu_execution_reservation drop constraint reservation_test_failure");}
        tx.execute(s->worker(runtime,CLOCK).reserve(e));
        assertThat(commitments(e)).isOne();
    }
    @Test void concurrentWorkerReplayObservesAndPinsOnlyOnce() throws Exception {
        var e=seed(context(CHAIN,"b","7"),"PAY-WORKER-DUPLICATE","4","0.1");
        var runtime=rpcFor(e);var worker=worker(runtime,CLOCK);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<BesuExecutionReservationService.Reservation> reserve=()->{start.await();return tx.execute(s->worker.reserve(e));};
            var a=pool.submit(reserve);var b=pool.submit(reserve);start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
            assertThat(commitments(e)).isOne();
            verify(runtime).observe(e.instruction());
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentWorkersCannotOverAllocateTokenCapacity() throws Exception {
        var ctx=context(CHAIN,"b","8");
        var a=seed(ctx,"PAY-WORKER-CAPACITY-A","8","0.1");
        var b=seed(ctx,"PAY-WORKER-CAPACITY-B","8","0.1");
        var worker=worker(rpcFor(a,b),CLOCK);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            java.util.function.Function<SettlementBinding.Execution,Callable<Boolean>> attempt=e->()->{
                start.await();
                try {tx.execute(s->worker.reserve(e));return true;}
                catch(IllegalStateException error) {
                    assertThat(error).hasMessage("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT");return false;
                }
            };
            var one=pool.submit(attempt.apply(a));var two=pool.submit(attempt.apply(b));start.countDown();
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(commitments(a)+commitments(b)).isOne();
        } finally {pool.shutdownNow();}
    }
    @Test void workerCannotUseTermsWithoutAnOwnerClaim() {
        var e=execution(context(CHAIN,"b","9"),"PAY-WORKER-FORGED","1","0.1");
        var runtime=mock(BesuRuntime.class);
        assertThatThrownBy(()->tx.execute(s->worker(runtime,CLOCK).reserve(e)))
                .hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
        verifyNoInteractions(runtime);
        assertThat(commitments(e)).isZero();
    }
    @Test void existingUnsignedHoldCannotBeAdoptedByWorker() {
        var e=seed(context(CHAIN,"b","a"),"PAY-WORKER-PARTIAL","1","0.1");
        tx.executeWithoutResult(s->store.hold(e,observation(e)));
        var runtime=mock(BesuRuntime.class);
        assertThatThrownBy(()->tx.execute(s->worker(runtime,CLOCK).reserve(e)))
                .hasMessage("BESU_RESERVATION_REQUIRES_RECOVERY");
        verifyNoInteractions(runtime);
        assertThat(jdbc.queryForObject("select status from besu_liquidity_commitment where execution_id=?",String.class,e.executionId())).isEqualTo("HELD");
    }
    static org.web3j.crypto.Credentials signingWallet(String key) {
        return org.web3j.crypto.Credentials.create(key); // disposable test key
    }
    static SettlementBinding.Execution signedExecution(String key,String payment,String amount) {
        var base=context(CHAIN,"b","d");
        var ctx=new SettlementNetwork.Context(base.token(),base.nativeAsset(),signingWallet(key).getAddress(),
                base.feeModel(),base.policyVersion());
        var e=seed(ctx,payment,amount,"0.1");
        tx.execute(s->worker(rpcFor(e),CLOCK).reserve(e));
        return e;
    }
    static BesuSignedTransfer signature(SettlementBinding.Execution e,String key) {
        String data="0xa9059cbb"+"0".repeat(24)+e.instruction().recipient().substring(2)
                +org.web3j.utils.Numeric.toHexStringNoPrefixZeroPadded(e.instruction().tokenAmount().movePointRight(6).toBigIntegerExact(),64);
        byte[] bytes=org.web3j.crypto.TransactionEncoder.signMessage(org.web3j.crypto.RawTransaction.createTransaction(
                java.math.BigInteger.ZERO,java.math.BigInteger.valueOf(1000),java.math.BigInteger.valueOf(25000),
                e.context().token().contract(),java.math.BigInteger.ZERO,data),BesuSettings.CHAIN_ID,signingWallet(key));
        return new BesuSignedTransfer(e,0,new java.math.BigDecimal("25000"),new java.math.BigDecimal("1000"),bytes);
    }
    @Test void signedPayloadRoundTripsAndCannotBeRewrittenOrDeleted() {
        var e=signedExecution("31","PAY-SIGNED-IMMUTABLE","1"); var signed=signature(e,"31");
        var submissions=new BesuSubmissionStore(jdbc);
        tx.executeWithoutResult(s->{store.lock(e.context());submissions.save(signed);});
        var restored=submissions.find(e).orElseThrow();
        assertThat(restored.transfer().payload()).isEqualTo(signed.payload());
        assertThat(restored.transfer().hash).isEqualTo(signed.hash);
        assertThatThrownBy(()->jdbc.update("update besu_signed_submission set nonce=1 where execution_id=?",e.executionId()))
                .hasMessageContaining("BESU_SIGNED_SUBMISSION_IMMUTABLE");
        assertThatThrownBy(()->jdbc.update("delete from besu_signed_submission where execution_id=?",e.executionId()))
                .hasMessageContaining("BESU_SIGNED_SUBMISSION_IMMUTABLE");
    }
    @Test void signedRowAndAuditRollbackTogether() {
        var e=signedExecution("32","PAY-SIGNED-ROLLBACK","1"); var submissions=new BesuSubmissionStore(jdbc);
        jdbc.execute("alter table besu_submission_audit add constraint audit_test_failure check(false) not valid");
        try {
            assertThatThrownBy(()->tx.executeWithoutResult(s->{store.lock(e.context());submissions.save(signature(e,"32"));}))
                    .hasMessageContaining("audit_test_failure");
            assertThat(submissions.find(e)).isEmpty();
        } finally {jdbc.execute("alter table besu_submission_audit drop constraint audit_test_failure");}
    }
    @Test void attemptAndAuditRollbackTogetherWithoutLosingOriginalSignature() {
        var e=signedExecution("33","PAY-ATTEMPT-ROLLBACK","1"); var submissions=new BesuSubmissionStore(jdbc);
        tx.executeWithoutResult(s->{store.lock(e.context());submissions.save(signature(e,"33"));});
        jdbc.execute("alter table besu_submission_audit add constraint attempt_test_failure check(status<>'ATTEMPTED') not valid");
        try {
            assertThatThrownBy(()->tx.executeWithoutResult(s->{store.lock(e.context());submissions.transition(e,"SIGNED","ATTEMPTED");}))
                    .hasMessageContaining("attempt_test_failure");
            assertThat(submissions.find(e).orElseThrow().status()).isEqualTo("SIGNED");
        } finally {jdbc.execute("alter table besu_submission_audit drop constraint attempt_test_failure");}
    }
    @Test void sameTreasuryCannotReuseNonceForAnotherExecution() {
        var a=signedExecution("34","PAY-NONCE-A","1"); var b=signedExecution("34","PAY-NONCE-B","2");
        var submissions=new BesuSubmissionStore(jdbc);
        tx.executeWithoutResult(s->{store.lock(a.context());submissions.save(signature(a,"34"));});
        assertThatThrownBy(()->tx.executeWithoutResult(s->{store.lock(b.context());submissions.save(signature(b,"34"));}))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(submissions.find(b)).isEmpty();
        assertThatThrownBy(()->tx.executeWithoutResult(s->{store.lock(b.context());submissions.requireNoOtherSubmission(b);}))
                .hasMessage("BESU_TREASURY_SUBMISSION_UNRESOLVED");
    }
    @Test void concurrentDeliveriesCanOwnOnlyOneAttempt() throws Exception {
        var e=signedExecution("35","PAY-ATTEMPT-ONCE","1"); var submissions=new BesuSubmissionStore(jdbc);
        tx.executeWithoutResult(s->{store.lock(e.context());submissions.save(signature(e,"35"));});
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt=()->{start.await();return tx.execute(s->{
                store.lock(e.context());
                if (!"SIGNED".equals(submissions.find(e).orElseThrow().status())) return false;
                submissions.transition(e,"SIGNED","ATTEMPTED");return true;
            });};
            var a=pool.submit(attempt);var b=pool.submit(attempt);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally {pool.shutdownNow();}
    }
    @Test void unknownCannotBecomeUnsignedAndAuditCannotBeDeleted() {
        var e=signedExecution("36","PAY-UNKNOWN-GUARD","1");var submissions=new BesuSubmissionStore(jdbc);
        tx.executeWithoutResult(s->{store.lock(e.context());submissions.save(signature(e,"36"));
            submissions.transition(e,"SIGNED","ATTEMPTED");submissions.transition(e,"ATTEMPTED","UNKNOWN");});
        assertThatThrownBy(()->jdbc.update("update besu_signed_submission set status='SIGNED' where execution_id=?",e.executionId()))
                .hasMessageContaining("BESU_SIGNED_SUBMISSION_IMMUTABLE");
        assertThatThrownBy(()->jdbc.update("delete from besu_submission_audit where execution_id=?",e.executionId()))
                .hasMessageContaining("BESU_OBSERVATION_IMMUTABLE");
        tx.executeWithoutResult(s->{store.lock(e.context());submissions.transition(e,"UNKNOWN","SUBMITTED");});
        assertThat(submissions.find(e).orElseThrow().status()).isEqualTo("SUBMITTED");
    }
    @Test void submissionWorkerCommitsBeforeBroadcastAndRestartCannotBroadcastAgain() {
        var e=signedExecution("37","PAY-SUBMIT-WORKER","1");
        var runtime=rpcFor(e);var adapter=mock(BesuBlockchainAdapter.class);
        when(runtime.forPinnedExecution(e.context())).thenReturn(adapter);
        when(adapter.signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any()))
                .thenReturn(signature(e,"37").payload());
        when(adapter.submitRawTransaction(any())).thenAnswer(c->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(new BesuSubmissionStore(jdbc).find(e).orElseThrow().status()).isEqualTo("ATTEMPTED");
            throw new IllegalStateException("RPC_TIMEOUT");
        });
        var worker=new BesuSubmissionService(runtime,new SettlementExecutionReads(jdbc),mock(SettlementPreparationReads.class),mock(SettlementFundingReads.class),
                store,new BesuSubmissionStore(jdbc),tx,CLOCK);
        assertThat(worker.submit(e).status()).isEqualTo("UNKNOWN");
        when(adapter.observesTransaction(any())).thenReturn(true);
        var restarted=new BesuSubmissionService(runtime,new SettlementExecutionReads(jdbc),mock(SettlementPreparationReads.class),mock(SettlementFundingReads.class),
                store,new BesuSubmissionStore(jdbc),tx,Clock.offset(CLOCK,Duration.ofSeconds(600)));
        assertThat(restarted.submit(e).status()).isEqualTo("SUBMITTED");
        verify(adapter).submitRawTransaction(any());
        verify(adapter).signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any());
        assertThat(jdbc.queryForObject("select count(*) from besu_submission_audit where execution_id=?",Integer.class,e.executionId())).isEqualTo(4);
    }
    static BesuSignedTransfer attempted(String key,String payment) {
        var e=signedExecution(key,payment,"1"); var t=signature(e,key); var submissions=new BesuSubmissionStore(jdbc);
        tx.executeWithoutResult(s->{store.lock(e.context());submissions.save(t);submissions.transition(e,"SIGNED","ATTEMPTED");
            submissions.transition(e,"ATTEMPTED","UNKNOWN");});
        return t;
    }
    static BesuFinalReceipt finalReceipt(BesuSignedTransfer t,boolean success) {
        return new BesuFinalReceipt(t.execution.executionId(),t.hash,BesuBlockchainAdapterTest.BLOCK,101,success,
                new java.math.BigDecimal("21000"),new java.math.BigDecimal("1000"),java.math.BigInteger.valueOf(21000000));
    }
    static void resolve(BesuSignedTransfer t,boolean success) {
        var submissions=new BesuSubmissionStore(jdbc);
        tx.executeWithoutResult(s->{store.lock(t.execution.context());
            submissions.resolve(submissions.find(t.execution).orElseThrow(),finalReceipt(t,success));});
    }
    @Test void finalReceiptResolvesCapacityButOldBalanceCannotMakeSpentFundsAvailable() {
        var t=attempted("41","PAY-RECEIPT-CAPACITY"); var e=t.execution; resolve(t,true);
        var submissions=new BesuSubmissionStore(jdbc);
        assertThat(submissions.find(e).orElseThrow().status()).isEqualTo("CONFIRMED");
        assertThat(submissions.receipt(t)).contains(finalReceipt(t,true));
        var next=execution(e.context(),"PAY-RECEIPT-NEXT","1","0.1");
        assertThatThrownBy(()->tx.execute(s->store.snapshot(evidence(next)))).hasMessage("BESU_BALANCE_PREDATES_RESOLUTION");
        var o=observation(next);
        var fresh=new SettlementObservation(next.instruction(),101,new java.math.BigDecimal("9"),o.nativeTotal(),
                o.estimatedGas(),o.gasLimit(),o.gasPriceWei(),o.executionFeeEstimate(),NOW,NOW.plusSeconds(15));
        var ev=new SettlementPreparation.Evidence(new SettlementPreparation.Request(UUID.randomUUID(),next.instruction(),NOW),fresh);
        var available=tx.execute(s->store.snapshot(ev));
        assertThat(available.tokenCommitted()).isZero(); assertThat(available.nativeCommitted()).isZero();
        tx.executeWithoutResult(s->{store.lock(e.context());submissions.requireNoOtherSubmission(next);});
        assertThat(jdbc.queryForObject("select status from besu_liquidity_commitment where execution_id=?",String.class,e.executionId())).isEqualTo("PINNED");
    }
    @Test void revertedReceiptResolvesWithGasEvidenceAndCannotBeRewritten() {
        var t=attempted("42","PAY-RECEIPT-REVERTED"); resolve(t,false);
        var submissions=new BesuSubmissionStore(jdbc);
        assertThat(submissions.find(t.execution).orElseThrow().status()).isEqualTo("REVERTED");
        assertThat(submissions.receipt(t).orElseThrow().totalFeeWei()).isEqualTo(java.math.BigInteger.valueOf(21000000));
        for(var sql:List.of("delete from besu_final_receipt where execution_id=?",
                "update besu_final_receipt set success=true where execution_id=?"))
            assertThatThrownBy(()->jdbc.update(sql,t.execution.executionId())).hasMessageContaining("BESU_OBSERVATION_IMMUTABLE");
        assertThatThrownBy(()->jdbc.update("update besu_signed_submission set status='SUBMITTED' where execution_id=?",t.execution.executionId()))
                .hasMessageContaining("BESU_SIGNED_SUBMISSION_IMMUTABLE");
    }
    @Test void receiptCannotCommitWithoutMatchingTerminalStatusAndAudit() {
        var t=attempted("43","PAY-RECEIPT-ORPHAN");
        assertThatThrownBy(()->tx.executeWithoutResult(s->jdbc.update(
                "insert into besu_final_receipt(execution_id,scope_key,tx_hash,block_hash,block_number,success,gas_used,gas_price,total_fee_wei) values (?,?,?,?,101,true,21000,1000,21000000)",
                t.execution.executionId(),t.execution.context().nonceScope(),t.hash,BesuBlockchainAdapterTest.BLOCK)))
                .isInstanceOf(org.springframework.transaction.TransactionSystemException.class)
                .rootCause().isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("BESU_RECEIPT_RESOLUTION_NOT_ATOMIC");
        assertThat(new BesuSubmissionStore(jdbc).receipt(t)).isEmpty();
        assertThatThrownBy(()->jdbc.update("update besu_signed_submission set status='CONFIRMED' where execution_id=?",t.execution.executionId()))
                .hasMessageContaining("BESU_SIGNED_SUBMISSION_IMMUTABLE");
    }
    @Test void receiptAndCapacityResolutionRollBackOnAuditFailure() {
        var t=attempted("44","PAY-RECEIPT-ROLLBACK");
        jdbc.execute("alter table besu_submission_audit add constraint receipt_audit_failure check(status<>'CONFIRMED') not valid");
        try {
            assertThatThrownBy(()->resolve(t,true)).hasMessageContaining("receipt_audit_failure");
            var submissions=new BesuSubmissionStore(jdbc);
            assertThat(submissions.receipt(t)).isEmpty();
            assertThat(submissions.find(t.execution).orElseThrow().status()).isEqualTo("UNKNOWN");
            assertThat(tx.execute(s->store.snapshot(evidence(t.execution))).tokenCommitted()).isEqualByComparingTo("1");
        } finally {jdbc.execute("alter table besu_submission_audit drop constraint receipt_audit_failure");}
    }
    @Test void concurrentReceiptWorkersRecordOneResolutionAndReplay() throws Exception {
        var t=attempted("45","PAY-RECEIPT-CONCURRENT"); var runtime=mock(BesuRuntime.class); var adapter=mock(BesuBlockchainAdapter.class);
        when(runtime.forPinnedExecution(t.execution.context())).thenReturn(adapter);
        var both=new CountDownLatch(2);
        when(adapter.finalReceipt(any())).thenAnswer(c->{both.countDown();
            assertThat(both.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(finalReceipt(t,true));});
        var worker=new BesuReceiptService(runtime,new SettlementExecutionReads(jdbc),store,new BesuSubmissionStore(jdbc),tx);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->worker.confirm(t.execution));var b=pool.submit(()->worker.confirm(t.execution));
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
            assertThat(worker.confirm(t.execution).status()).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("select count(*) from besu_submission_audit where execution_id=? and status='CONFIRMED'",Integer.class,t.execution.executionId())).isOne();
            verify(adapter,times(2)).finalReceipt(any());
        } finally {pool.shutdownNow();}
    }
    @Test void discoveryResumesUnknownWithoutPaymentOrFundingAndExcludesFinalReceipts() {
        var t=attempted("61","PAY-WORK-DISCOVERY");var reads=new BesuExecutionWorkReads(jdbc);
        assertThat(recoveryRows(reads)).filteredOn(row->row.id().equals(t.execution.executionId())).singleElement()
            .satisfies(row->{assertThat(row.execution()).isEqualTo(t.execution);assertThat(row.status()).isEqualTo("UNKNOWN");});
        assertThat(jdbc.queryForObject("select count(*) from besu_signed_submission where execution_id=? and status='UNKNOWN'",Integer.class,t.execution.executionId())).isOne();
        resolve(t,true);
        assertThat(recoveryRows(reads)).noneMatch(row->row.id().equals(t.execution.executionId()));
    }
    @Test void fundedDiscoveryUsesCommittedPaymentStateAndSkipsSignedExecutions() {
        var e=signedExecution("62","PAY-WORK-FUNDED","1");var reads=new BesuExecutionWorkReads(jdbc);
        jdbc.update("insert into payment values (?, 'FUNDED')",e.instruction().paymentId());
        jdbc.update("insert into payment_besu_funding values (?,?,cast(? as jsonb),?)",e.executionId(),e.instruction().paymentId(),SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));
        assertThat(reads.funded(null)).anySatisfy(row->assertThat(row.execution()).isEqualTo(e));
        jdbc.update("update payment set status='RECONCILED' where payment_id=?",e.instruction().paymentId());
        assertThat(reads.funded(null)).noneMatch(row->row.id().equals(e.executionId()));
        jdbc.update("update payment set status='FUNDED' where payment_id=?",e.instruction().paymentId());
        var signed=signature(e,"62"); // Storage fixture; no network or authority claim.
        tx.executeWithoutResult(s->{store.lock(e.context());new BesuSubmissionStore(jdbc).save(signed);});
        assertThat(reads.funded(null)).noneMatch(row->row.id().equals(e.executionId()));
    }
    @Test void claimDiscoveryMovesFromUnreservedToPinnedToFundedWithoutCreatingAuthority() {
        var e=seed(context(CHAIN,"b","c"),"PAY-CLAIM-WORK-STAGES","1","0.1");
        var reads=new SettlementClaimWorkReads(jdbc);
        jdbc.update("insert into payment values (?, 'COMPLIANCE_APPROVED')",e.instruction().paymentId());
        assertThat(claimRows(reads,false)).contains(e.executionId());
        assertThat(claimRows(reads,true)).doesNotContain(e.executionId());
        assertThat(reads.execution(e.executionId())).isEqualTo(e);
        tx.executeWithoutResult(s->{store.hold(e,observation(e));store.pin(e);store.capturePinnedObservation(e,observation(e));});
        assertThat(claimRows(reads,false)).doesNotContain(e.executionId());
        assertThat(claimRows(reads,true)).contains(e.executionId());
        jdbc.update("insert into payment_besu_funding values (?,?,cast(? as jsonb),?)",e.executionId(),e.instruction().paymentId(),
            SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));
        assertThat(claimRows(reads,true)).doesNotContain(e.executionId());
        assertThat(claimRows(reads,false)).doesNotContain(e.executionId());
    }
    @Test void rolledBackReservationDoesNotBecomeFundingWorkAndChangedPaymentStopsDiscovery() {
        var e=seed(context(CHAIN,"b","f"),"PAY-CLAIM-WORK-ROLLBACK","1","0.1");
        var reads=new SettlementClaimWorkReads(jdbc);
        jdbc.update("insert into payment values (?, 'COMPLIANCE_APPROVED')",e.instruction().paymentId());
        assertThatThrownBy(()->tx.executeWithoutResult(s->{
            store.hold(e,observation(e));store.pin(e);store.capturePinnedObservation(e,observation(e));
            throw new IllegalStateException("OWNER_ROLLBACK");
        })).hasMessage("OWNER_ROLLBACK");
        assertThat(claimRows(reads,false)).contains(e.executionId());
        assertThat(claimRows(reads,true)).doesNotContain(e.executionId());
        jdbc.update("update payment set status='FAILED' where payment_id=?",e.instruction().paymentId());
        assertThat(claimRows(reads,false)).doesNotContain(e.executionId());
    }
    static List<UUID> claimRows(SettlementClaimWorkReads reads,boolean funded) {
        var all=new ArrayList<UUID>();UUID cursor=null;
        for(int page=0;page<100;page++) {
            var rows=funded?reads.awaitingFunding(cursor):reads.awaitingReservation(cursor);all.addAll(rows);
            if(rows.size()<SettlementClaimWorkReads.BATCH_SIZE)return all;
            cursor=rows.getLast();
        }
        throw new AssertionError("Claim keyset failed to reach end");
    }
    static List<BesuExecutionWorkReads.Candidate> recoveryRows(BesuExecutionWorkReads reads) {
        var all=new ArrayList<BesuExecutionWorkReads.Candidate>();UUID cursor=null;
        for(int page=0;page<100;page++) {
            var rows=reads.recovering(cursor);all.addAll(rows);
            if(rows.size()<BesuExecutionWorkReads.BATCH_SIZE)return all;
            cursor=rows.getLast().id();
        }
        throw new AssertionError("Recovery keyset failed to reach end");
    }
}
