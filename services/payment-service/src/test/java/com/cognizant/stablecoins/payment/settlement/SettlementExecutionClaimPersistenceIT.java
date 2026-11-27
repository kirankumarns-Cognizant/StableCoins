package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.payment.settlement.BindingFixtures.*;

/** Full payment migration chain and real JDBC transactions. Requires Docker; never silently skips.
 * These tests exercise storage, not canonical approval authorization or chain acceptance.
 */
@Testcontainers
class SettlementExecutionClaimPersistenceIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static SettlementExecutionClaimStore store;
    static Flyway flyway;

    @BeforeAll static void schema() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        flyway.migrate();
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.setTimeout(15);
        store = new SettlementExecutionClaimStore(jdbc);
    }

    static SettlementExecutionClaim seed() { return seed(jdbc,tx); }
    static SettlementExecutionClaim seed(JdbcTemplate jdbc,TransactionTemplate tx) {
        var i = instruction();
        var account = UUID.randomUUID();
        var beneficiary = UUID.randomUUID();
        jdbc.update("insert into customer(customer_id,name,customer_type) values (?,'Fictional claim customer','CORPORATE')", i.customerId());
        jdbc.update("insert into account(account_id,customer_id,currency,account_type) values (?,?,'USD','DEMO_SIMULATED_USD')", account, i.customerId());
        jdbc.update("insert into beneficiary(beneficiary_id,customer_id,name,country,currency,wallet_address) values (?,?,'Fictional claim supplier','USA','USD',?)", beneficiary, i.customerId(), i.recipient());
        jdbc.update("insert into payment(payment_id,correlation_id,sender_account_id,beneficiary_id,amount,source_currency,destination_currency,payment_rail,purpose,status,idempotency_key,request_fingerprint) values (?,?,?,?,?,'USD','USD','STABLECOIN','SUPPLIER_PAYMENT','COMPLIANCE_APPROVED',?,?)",
                i.paymentId(), i.correlationId(), account, beneficiary, i.sourceUsd(), UUID.randomUUID().toString(), "a".repeat(64));
        var quote = SettlementQuoteFixtures.quote(i, UUID.randomUUID(), UUID.randomUUID());
        var review = quote.review();
        var request = quote.availability().preparation().request();
        tx.executeWithoutResult(s -> {
            new SettlementBindingStore(jdbc).captureInstruction(i);
            jdbc.update("insert into payment_settlement_preparation(request_id,instruction_id,payment_id,correlation_id,actor_id,document,fingerprint) values (?,?,?,?,'operator',cast(? as jsonb),?)",
                    request.requestId(), i.intentId(), i.paymentId(), i.correlationId(), SettlementBindingJson.write(request), SettlementBindingJson.fingerprint(request));
            new SettlementQuoteStore(jdbc).capture(quote, "operator");
        });
        // Storage fixture only: service-level tests own actual review/acceptance/vote authorization.
        jdbc.update("insert into routing_recommendation(recommendation_id,payment_id,correlation_id,customer_id,version,required_approvals,state,document,created_at,expires_at) values (?,?,?,?,?,2,'APPROVED','{}'::jsonb,?,?)",
                review.recommendationId(), i.paymentId(), i.correlationId(), i.customerId(), review.version(), Timestamp.from(review.assessedAt()), Timestamp.from(review.expiresAt()));
        var accepted = review.assessedAt().plusSeconds(1);
        var approved = accepted.plusSeconds(1);
        return new SettlementExecutionClaim(new SettlementBinding.Execution(UUID.randomUUID(), review), "operator", "customer-v1", 2,
                List.of(new SettlementExecutionClaim.Approval("first", UUID.randomUUID(), accepted, UUID.randomUUID(), approved),
                        new SettlementExecutionClaim.Approval("second", UUID.randomUUID(), accepted, UUID.randomUUID(), approved)), approved.plusSeconds(1));
    }

    static int claims(SettlementExecutionClaim c) {
        return jdbc.queryForObject("select count(*) from payment_settlement_execution_claim where payment_id=?", Integer.class, c.execution().instruction().paymentId());
    }
    static int audits(SettlementExecutionClaim c) {
        return jdbc.queryForObject("select count(*) from routing_audit where payment_id=? and action='SETTLEMENT_EXECUTION_CLAIMED'", Integer.class, c.execution().instruction().paymentId());
    }
    static void capture(SettlementExecutionClaim c) { tx.executeWithoutResult(s -> store.capture(c)); }
    static SettlementExecutionClaim anotherExecution(SettlementExecutionClaim c) {
        return new SettlementExecutionClaim(new SettlementBinding.Execution(UUID.randomUUID(), c.execution().review()),
                c.requestedBy(), c.customerPolicyVersion(), c.requiredApprovals(), c.approvals(), c.claimedAt());
    }
    static void concurrently(Runnable first, Runnable second) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> { await(start); first.run(); });
            var two = pool.submit(() -> { await(start); second.run(); });
            start.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
    static void await(CountDownLatch start) {
        try { if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("START_TIMEOUT"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @Test void fullMigrationChainValidatesAndReappliesWithoutChanges() {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("20");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
    @Test void jsonbAndTimestampRoundTripReplaysWithoutAnotherAudit() {
        var c = seed(); capture(c); capture(c);
        assertThat(store.byId(c.execution().executionId())).contains(c);
        assertThat(store.byPayment(c.execution().instruction().paymentId())).contains(c);
        assertThat(claims(c)).isOne(); assertThat(audits(c)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from payment_execution", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from payment_settlement_binding where phase='EXECUTION'", Integer.class)).isZero();
    }
    @Test void claimCannotBeUpdatedOrDeleted() {
        var c = seed(); capture(c); var id = c.execution().executionId();
        assertThatThrownBy(() -> jdbc.update("update payment_settlement_execution_claim set actor_id='changed' where execution_id=?", id))
                .hasMessageContaining("SETTLEMENT_BINDING_IMMUTABLE");
        assertThatThrownBy(() -> jdbc.update("delete from payment_settlement_execution_claim where execution_id=?", id))
                .hasMessageContaining("SETTLEMENT_BINDING_IMMUTABLE");
        assertThat(store.byId(id)).contains(c);
    }
    @Test void outerFailureRollsBackClaimAndAuditTogether() {
        var c = seed();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> { store.capture(c); throw new IllegalStateException("OWNER_ROLLBACK"); }))
                .hasMessage("OWNER_ROLLBACK");
        assertThat(claims(c)).isZero(); assertThat(audits(c)).isZero();
        capture(c); assertThat(claims(c)).isOne(); assertThat(audits(c)).isOne();
    }
    @Test void auditFailureCannotCommitClaim() {
        var c = seed();
        jdbc.execute("alter table routing_audit add constraint claim_test_audit_failure check (action <> 'SETTLEMENT_EXECUTION_CLAIMED') not valid");
        try {
            assertThatThrownBy(() -> capture(c)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(claims(c)).isZero(); assertThat(audits(c)).isZero();
        } finally { jdbc.execute("alter table routing_audit drop constraint claim_test_audit_failure"); }
    }
    @Test void concurrentExactReplayCreatesOneClaimAndOneAudit() throws Exception {
        var c = seed(); concurrently(() -> capture(c), () -> capture(c));
        assertThat(claims(c)).isOne(); assertThat(audits(c)).isOne();
        assertThat(store.byId(c.execution().executionId())).contains(c);
    }
    @Test void concurrentDifferentExecutionIdsHaveExactlyOneWinner() throws Exception {
        var first = seed(); var second = anotherExecution(first);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        var conflicts = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<SettlementExecutionClaim> attempt = c -> {
            try { capture(c); successes.incrementAndGet(); }
            catch (IllegalStateException e) {
                assertThat(e).hasMessage("SETTLEMENT_EXECUTION_CLAIM_CONFLICT"); conflicts.incrementAndGet();
            }
        };
        concurrently(() -> attempt.accept(first), () -> attempt.accept(second));
        assertThat(successes.get()).isOne(); assertThat(conflicts.get()).isOne();
        assertThat(claims(first)).isOne(); assertThat(audits(first)).isOne();
    }
    @Test void executionIdCannotBeReusedAcrossPayments() {
        var first = seed(); var other = seed(); capture(first);
        var duplicate = new SettlementExecutionClaim(new SettlementBinding.Execution(first.execution().executionId(), other.execution().review()),
                other.requestedBy(), other.customerPolicyVersion(), other.requiredApprovals(), other.approvals(), other.claimedAt());
        assertThatThrownBy(() -> capture(duplicate)).hasMessage("SETTLEMENT_EXECUTION_CLAIM_CONFLICT");
        assertThat(claims(other)).isZero(); assertThat(audits(other)).isZero();
        assertThat(store.byId(first.execution().executionId())).contains(first);
    }
    @Test void changedOwnerScopeIsRejectedBeforeCapture() {
        var c = seed(); var i = c.execution().instruction();
        jdbc.update("update payment set correlation_id=? where payment_id=?", "CORR-CHANGED-" + UUID.randomUUID(), i.paymentId());
        assertThatThrownBy(() -> capture(c)).hasMessage("SETTLEMENT_PAYMENT_SCOPE_MISMATCH");
        assertThat(claims(c)).isZero(); assertThat(audits(c)).isZero();
    }

    /** Bypass the DAO to establish database FK/unique protection, using only fixed test column names. */
    static void rawInsert(SettlementExecutionClaim c, Map<String,Object> overrides) {
        var e = c.execution(); var r = e.review(); var i = e.instruction();
        var values = new LinkedHashMap<String,Object>();
        values.put("execution_id", e.executionId()); values.put("payment_id", i.paymentId());
        values.put("correlation_id", i.correlationId()); values.put("customer_id", i.customerId());
        values.put("instruction_id", i.intentId()); values.put("recommendation_id", r.recommendationId());
        values.put("recommendation_version", r.version()); values.put("quote_id", r.quoteId());
        values.put("quote_version", r.quoteVersion()); values.put("preparation_id", r.preparationId());
        values.put("actor_id", c.requestedBy()); values.put("claimed_at", Timestamp.from(c.claimedAt()));
        values.put("document", SettlementExecutionClaimJson.write(c)); values.put("fingerprint", SettlementExecutionClaimJson.fingerprint(c));
        values.put("execution_document",null); values.put("execution_fingerprint",null);
        assertThat(values.keySet()).containsAll(overrides.keySet()); values.putAll(overrides);
        String placeholders = String.join(",", values.keySet().stream().map(k -> Set.of("document","execution_document").contains(k) ? "cast(? as jsonb)" : "?").toList());
        jdbc.update("insert into payment_settlement_execution_claim(" + String.join(",", values.keySet()) + ") values (" + placeholders + ")", values.values().toArray());
    }
    @Test void compositeForeignKeysRejectCrossCustomerVersionQuoteAndPreparation() {
        var c = seed(); var other = seed(); var r = other.execution().review();
        for (var changed : List.<Map<String,Object>>of(
                Map.of("customer_id", other.execution().instruction().customerId()),
                Map.of("recommendation_version", 99), Map.of("correlation_id", "CORR-WRONG"),
                Map.of("quote_id", r.quoteId()), Map.of("preparation_id", r.preparationId()),
                Map.of("instruction_id", other.execution().instruction().intentId()))) {
            assertThatThrownBy(() -> rawInsert(c, changed)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(claims(c)).isZero();
        }
    }
    @Test void uniquePaymentConstraintRejectsBypassedSecondClaim() {
        var c = seed(); capture(c);
        assertThatThrownBy(() -> rawInsert(anotherExecution(c), Map.of())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(claims(c)).isOne(); assertThat(audits(c)).isOne();
    }
    @Test void readDetectsDocumentColumnAndFingerprintMismatch() {
        var c = seed(); rawInsert(c, Map.of("actor_id", "different-actor"));
        assertThatThrownBy(() -> store.byId(c.execution().executionId())).hasMessage("SETTLEMENT_CLAIM_INTEGRITY_FAILED");
        var other = seed(); rawInsert(other, Map.of("fingerprint", "0".repeat(64)));
        assertThatThrownBy(() -> store.byPayment(other.execution().instruction().paymentId())).hasMessage("SETTLEMENT_CLAIM_INTEGRITY_FAILED");
    }
    @Test void claimProjectionCannotDisagreeWithItsOwnerDocumentOrBeAddedRetrospectively() {
        var c=seed(); capture(c);
        new SettlementExecutionReads(jdbc).requireClaimed(c.execution());
        assertThatThrownBy(()->jdbc.update("update payment_settlement_execution_claim set execution_fingerprint=? where execution_id=?",
                "0".repeat(64),c.execution().executionId())).hasMessageContaining("SETTLEMENT_BINDING_IMMUTABLE");
        var old=seed(); rawInsert(old,Map.of());
        assertThatThrownBy(()->new SettlementExecutionReads(jdbc).requireClaimed(old.execution()))
                .hasMessage("INVALID_SETTLEMENT_BINDING");
        var other=seed();
        assertThatThrownBy(()->rawInsert(other,Map.of("execution_document",SettlementBindingJson.write(c.execution()),
                "execution_fingerprint",SettlementBindingJson.fingerprint(c.execution()))))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("settlement_claim_execution_projection");
    }
}
