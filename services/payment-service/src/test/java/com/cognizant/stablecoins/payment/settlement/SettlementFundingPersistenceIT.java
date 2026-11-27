package com.cognizant.stablecoins.payment.settlement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real owner storage/transaction tests. No RPC or mocked PostgreSQL; requires Docker.
 * The JDBC harness models owner debit/outbox writes; service authorization is tested separately.
 */
@Testcontainers
class SettlementFundingPersistenceIT {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;static TransactionTemplate tx;static SettlementFundingStore store;static Flyway flyway;
    @BeforeAll static void schema(){
        var ds=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();flyway.migrate();
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));tx.setTimeout(15);
        store=new SettlementFundingStore(jdbc);
    }
    SettlementExecutionClaim seed(){
        var c=SettlementExecutionClaimPersistenceIT.seed(jdbc,tx);
        tx.executeWithoutResult(s->new SettlementExecutionClaimStore(jdbc).capture(c));
        jdbc.update("update account set balance=20 where account_id=(select sender_account_id from payment where payment_id=?)",c.execution().instruction().paymentId());
        return c;
    }
    SettlementFundingService.Receipt fund(SettlementExecutionClaim c,boolean fail){
        return tx.execute(s->{
            var e=c.execution();var i=e.instruction();
            var account=jdbc.queryForObject("select sender_account_id from payment where payment_id=? for update",UUID.class,i.paymentId());
            var prior=store.find(e);if(prior.isPresent())return prior.get();
            var balance=jdbc.queryForObject("select balance from account where account_id=? for update",BigDecimal.class,account);
            var receipt=new SettlementFundingService.Receipt(e,account,balance,balance.subtract(i.sourceUsd()),"operator",UUID.randomUUID(),c.claimedAt());
            jdbc.update("update account set balance=? where account_id=?",receipt.balanceAfter(),account);
            jdbc.update("insert into payment_outbox_event(event_id,event_type,event_version,aggregate_id,correlation_id,payload,status) values (?,'BesuFundingAuthorized','1.0',?,?,'{}','PENDING')",
                    receipt.eventId().toString(),i.paymentId(),i.correlationId());
            store.capture(receipt);
            if(fail)throw new IllegalStateException("ROLLBACK_PROBE");return receipt;
        });
    }
    @Test void migrationValidatesAndIsRepeatable(){flyway.validate();assertThat(flyway.info().current().getVersion().toString()).isEqualTo("20");assertThat(flyway.migrate().migrationsExecuted).isZero();}
    @Test void persistedFundingReplaysWithoutSecondDebitOrEvent(){
        var c=seed();var first=fund(c,false);var replay=fund(c,false);
        assertThat(replay.eventId()).isEqualTo(first.eventId());assertThat(replay.balanceAfter()).isEqualByComparingTo("15");
        assertThat(jdbc.queryForObject("select balance from account where account_id=?",BigDecimal.class,first.accountId())).isEqualByComparingTo("15");
        assertThat(jdbc.queryForObject("select count(*) from payment_outbox_event where aggregate_id=? and event_type='BesuFundingAuthorized'",Integer.class,c.execution().instruction().paymentId())).isOne();
    }
    @Test void failedTransactionRollsBackDebitOutboxAndAcknowledgement(){
        var c=seed();assertThatThrownBy(()->fund(c,true)).hasMessage("ROLLBACK_PROBE");
        assertThat(store.find(c.execution())).isEmpty();
        assertThat(jdbc.queryForObject("select a.balance from account a join payment p on p.sender_account_id=a.account_id where p.payment_id=?",BigDecimal.class,c.execution().instruction().paymentId())).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("select count(*) from payment_outbox_event where aggregate_id=? and event_type='BesuFundingAuthorized'",Integer.class,c.execution().instruction().paymentId())).isZero();
    }
    @Test void concurrentDuplicateFundingCommitsOneDebitAndEvent() throws Exception {
        var c=seed();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<SettlementFundingService.Receipt> run=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("START_TIMEOUT");return fund(c,false);};
            var a=pool.submit(run);var b=pool.submit(run);start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS).eventId()).isEqualTo(b.get(20,TimeUnit.SECONDS).eventId());
            assertThat(jdbc.queryForObject("select count(*) from payment_besu_funding where execution_id=?",Integer.class,c.execution().executionId())).isOne();
        }finally{pool.shutdownNow();}
    }
    @Test void acknowledgementCannotBeEditedOrDeleted(){
        var c=seed();fund(c,false);
        assertThatThrownBy(()->jdbc.update("update payment_besu_funding set balance_after=0 where execution_id=?",c.execution().executionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from payment_besu_funding where execution_id=?",c.execution().executionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void unbalancedDebitIsRejectedByDatabase(){
        var c=seed();var e=c.execution();var i=e.instruction();var event=UUID.randomUUID().toString();
        var account=jdbc.queryForObject("select sender_account_id from payment where payment_id=?",UUID.class,i.paymentId());
        jdbc.update("insert into payment_outbox_event(event_id,event_type,event_version,aggregate_id,correlation_id,payload,status) values (?,'BesuFundingAuthorized','1.0',?,?,'{}','PENDING')",event,i.paymentId(),i.correlationId());
        assertThatThrownBy(()->jdbc.update("""
                insert into payment_besu_funding(execution_id,payment_id,correlation_id,customer_id,account_id,source_usd,
                  balance_before,balance_after,actor_id,event_id,funded_at,funding_reference,execution_document,execution_fingerprint)
                values (?,?,?,?,?,?,20,0,'operator',?,?,?,cast(? as jsonb),?)
                """,e.executionId(),i.paymentId(),i.correlationId(),i.customerId(),account,i.sourceUsd(),event,
                java.sql.Timestamp.from(c.claimedAt()),"SIMULATED-BESU-"+e.executionId(),
                com.cognizant.stablecoins.flow.SettlementBindingJson.write(e),com.cognizant.stablecoins.flow.SettlementBindingJson.fingerprint(e)))
                .rootCause().hasMessageContaining("besu_funding_balanced_debit");
    }
    // Real Flyway/SQL guard tests; JDBC harness, not a claim of JPA/cross-owner runtime acceptance.
    void close(SettlementExecutionClaim c) {
        jdbc.update("""
            insert into payment_besu_closure(execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,report_id,source_hash)
            select execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,?,? from payment_besu_funding where execution_id=?
            """,UUID.randomUUID(),"a".repeat(64),c.execution().executionId());
    }
    @Test void closureRequiresExactFundingAndReconciledOwnerStateThenIsImmutable() {
        var c=seed();fund(c,false);
        assertThatThrownBy(()->close(c)).rootCause().hasMessageContaining("BESU_PAYMENT_CLOSURE_EVIDENCE_REQUIRED");
        jdbc.update("update payment set status='RECONCILED' where payment_id=?",c.execution().instruction().paymentId());
        close(c);
        assertThat(jdbc.queryForObject("select count(*) from payment_besu_closure where execution_id=?",Integer.class,c.execution().executionId())).isOne();
        assertThatThrownBy(()->jdbc.update("update payment_besu_closure set source_hash=? where execution_id=?","b".repeat(64),c.execution().executionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from payment_besu_closure where execution_id=?",c.execution().executionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void closureCannotSubstituteAnotherExecutionFingerprint() {
        var c=seed();fund(c,false);jdbc.update("update payment set status='RECONCILED' where payment_id=?",c.execution().instruction().paymentId());
        assertThatThrownBy(()->jdbc.update("""
            insert into payment_besu_closure(execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,report_id,source_hash)
            select execution_id,payment_id,correlation_id,execution_document,?,?,? from payment_besu_funding where execution_id=?
            ""","b".repeat(64),UUID.randomUUID(),"a".repeat(64),c.execution().executionId())).rootCause().hasMessageContaining("BESU_PAYMENT_CLOSURE_EVIDENCE_REQUIRED");
    }
    @Test void closureAndOwnerStateRollBackTogether() {
        var c=seed();fund(c,false);var payment=c.execution().instruction().paymentId();
        var before=jdbc.queryForObject("select status from payment where payment_id=?",String.class,payment);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{
            jdbc.update("update payment set status='RECONCILED' where payment_id=?",payment);close(c);throw new IllegalStateException("ROLLBACK_PROBE");
        })).hasMessage("ROLLBACK_PROBE");
        assertThat(jdbc.queryForObject("select status from payment where payment_id=?",String.class,payment)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from payment_besu_closure where execution_id=?",Integer.class,c.execution().executionId())).isZero();
    }

    void failBesu(SettlementExecutionClaim c) {
        jdbc.update("""
            insert into payment_besu_failure(execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,source_hash,tx_hash)
            select execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,?,? from payment_besu_funding where execution_id=?
            ""","a".repeat(64),"0x"+"b".repeat(64),c.execution().executionId());
    }
    void authorizeBesu(SettlementExecutionClaim c,String actor,String hash) {
        jdbc.update("""
            insert into payment_besu_recovery_authorization(execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,source_hash,actor_id,action)
            select execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,?,?,'RELEASE_REVERTED_TOKEN_IN_FLIGHT'
            from payment_besu_funding where execution_id=?
            """,hash,actor,c.execution().executionId());
    }
    @Test void revertFailureRequiresFailedOwnerAndCannotBeEdited() {
        var c=seed();fund(c,false);
        assertThatThrownBy(()->failBesu(c)).rootCause().hasMessageContaining("BESU_FAILED_OWNER_EVIDENCE_REQUIRED");
        jdbc.update("update payment set status='FAILED' where payment_id=?",c.execution().instruction().paymentId());failBesu(c);
        assertThatThrownBy(()->jdbc.update("delete from payment_besu_failure where execution_id=?",c.execution().executionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update payment_besu_failure set source_hash=? where execution_id=?","c".repeat(64),c.execution().executionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void recoveryAuthorityRequiresExactFailureAndIndependentActor() {
        var c=seed();fund(c,false);jdbc.update("update payment set status='FAILED' where payment_id=?",c.execution().instruction().paymentId());failBesu(c);
        assertThatThrownBy(()->authorizeBesu(c,"operator","a".repeat(64))).rootCause().hasMessageContaining("BESU_RECOVERY_INDEPENDENT_APPROVER_REQUIRED");
        assertThatThrownBy(()->authorizeBesu(c,"approver","c".repeat(64))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        authorizeBesu(c,"approver","a".repeat(64));
        assertThatThrownBy(()->jdbc.update("delete from payment_besu_recovery_authorization where execution_id=?",c.execution().executionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update payment_besu_recovery_authorization set actor_id='other' where execution_id=?",c.execution().executionId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void failureAndAuthorizationRollBackWithOwnerState() {
        var c=seed();fund(c,false);var payment=c.execution().instruction().paymentId();var previous=jdbc.queryForObject("select status from payment where payment_id=?",String.class,payment);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{jdbc.update("update payment set status='FAILED' where payment_id=?",payment);failBesu(c);authorizeBesu(c,"approver","a".repeat(64));throw new IllegalStateException("ROLLBACK_PROBE");})).hasMessage("ROLLBACK_PROBE");
        assertThat(jdbc.queryForObject("select status from payment where payment_id=?",String.class,payment)).isEqualTo(previous);
        assertThat(jdbc.queryForObject("select count(*) from payment_besu_failure where execution_id=?",Integer.class,c.execution().executionId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from payment_besu_recovery_authorization where execution_id=?",Integer.class,c.execution().executionId())).isZero();
    }

}
