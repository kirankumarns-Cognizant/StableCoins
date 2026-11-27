package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.payment.settlement.BindingFixtures.*;

/**
 * Real PostgreSQL acceptance of V13, against minimal pre-existing owner tables.
 * Does not replace full application Flyway or end-to-end acceptance. Requires Docker;
 * intentionally does not silently skip if Docker is absent.
 */
@Testcontainers
class SettlementBindingPersistenceIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate transaction;
    static SettlementBindingStore store;

    @BeforeAll static void setup() throws Exception {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source); transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        store = new SettlementBindingStore(jdbc);
        jdbc.execute("create table account(account_id uuid primary key,customer_id uuid not null)");
        jdbc.execute("create table payment(payment_id varchar(50) primary key,correlation_id varchar(60) not null,sender_account_id uuid references account(account_id))");
        jdbc.execute("create table routing_recommendation(recommendation_id uuid primary key)");
        jdbc.execute("create table payment_flow_preparation(request_id uuid primary key)");
        jdbc.execute("create table payment_execution(execution_id uuid primary key)");
        try (var input = new ClassPathResource("db/migration/V13__immutable_settlement_bindings.sql").getInputStream()) {
            jdbc.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    static SettlementBinding.Instruction payment() {
        var i = instruction(); var account = UUID.randomUUID();
        jdbc.update("insert into account values (?,?)", account, i.customerId());
        jdbc.update("insert into payment values (?,?,?)", i.paymentId(), i.correlationId(), account);
        return i;
    }
    @Test void realJsonbReplayAndAppendOnlyTrigger() {
        var i = payment();
        transaction.executeWithoutResult(status -> { store.captureInstruction(i); store.captureInstruction(i); });
        assertThat(jdbc.queryForObject("select count(*) from payment_settlement_binding where payment_id=?",
                Integer.class, i.paymentId())).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("update payment_settlement_binding set fingerprint=? where binding_id=?",
                "0".repeat(64), i.intentId())).hasMessageContaining("SETTLEMENT_BINDING_IMMUTABLE");
        assertThatThrownBy(() -> jdbc.update("delete from payment_settlement_binding where binding_id=?", i.intentId()))
                .hasMessageContaining("SETTLEMENT_BINDING_IMMUTABLE");
    }
    @Test void exactParentAndOwnerReferencesAreRequiredAndExecutionReplays() {
        var i = payment(); var review = review(i);
        var execution = new SettlementBinding.Execution(UUID.randomUUID(), review);
        jdbc.update("insert into routing_recommendation values (?)", review.recommendationId());
        jdbc.update("insert into payment_flow_preparation values (?)", review.preparationId());
        jdbc.update("insert into payment_execution values (?)", execution.executionId());
        transaction.executeWithoutResult(status -> {
            store.captureInstruction(i); store.captureReview(review);
            store.captureExecution(execution); store.captureExecution(execution);
        });
        assertThat(store.execution(execution.executionId())).contains(execution);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                store.captureReview(review(changedRecipient(i))))).hasMessage("SETTLEMENT_PARENT_BINDING_CHANGED");
    }
    @Test void concurrentDuplicateCaptureProducesOneInstruction() {
        var i = payment();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = java.util.concurrent.CompletableFuture.runAsync(() ->
                    transaction.executeWithoutResult(status -> store.captureInstruction(i)), pool);
            var second = java.util.concurrent.CompletableFuture.runAsync(() ->
                    transaction.executeWithoutResult(status -> store.captureInstruction(i)), pool);
            java.util.concurrent.CompletableFuture.allOf(first, second).orTimeout(20, java.util.concurrent.TimeUnit.SECONDS).join();
            assertThat(jdbc.queryForObject("select count(*) from payment_settlement_binding where payment_id=?",
                    Integer.class, i.paymentId())).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void failedCaptureRollsBackAndNullReviewReferencesAreRejected() {
        var i = payment();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            store.captureInstruction(i); throw new IllegalStateException("ROLLBACK_FIXTURE");
        })).hasMessage("ROLLBACK_FIXTURE");
        assertThat(jdbc.queryForObject("select count(*) from payment_settlement_binding where payment_id=?",
                Integer.class, i.paymentId())).isZero();
        transaction.executeWithoutResult(status -> store.captureInstruction(i));
        assertThatThrownBy(() -> jdbc.update(
                "insert into payment_settlement_binding(binding_id,phase,payment_id,correlation_id,parent_id,parent_phase,document,fingerprint) values (?,'REVIEW',?,?,?,'INSTRUCTION','{}',?)",
                UUID.randomUUID(), i.paymentId(), i.correlationId(), i.intentId(), "a".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
