package com.cognizant.stablecoins.payment.approval;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.payment.domain.SupportedCurrency;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.cognizant.stablecoins.payment.routing.DeterministicRoutingAssessmentService;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.payment.settlement.*;
import com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JPA approval/decision repositories, Flyway schema and JDBC claim/audit transactions.
 * Only the preparation/quote-owner read boundary is substituted with persisted fixture reads.
 * No RPC, wallet, financial consumer or customer activation. Requires Docker; never silently skips.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(SettlementApprovalClaimPersistenceIT.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementApprovalClaimPersistenceIT {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @TestConfiguration static class Config {
        @Bean RoutingPolicyProperties policies() { return new RoutingPolicyProperties(); }
        @Bean CustomerAccessProperties access() { return new CustomerAccessProperties(); }
        @Bean SettlementSelectionProperties selections() { return new SettlementSelectionProperties(); }
        @Bean SettlementQuoteService quoteOwner(JdbcTemplate jdbc) {
            var owner = mock(SettlementQuoteService.class);
            when(owner.get(anyString(), any(), any(), any())).thenAnswer(call -> {
                String paymentId = call.getArgument(0);
                UUID preparationId = call.getArgument(1), reviewId = call.getArgument(2);
                AuthorizedCustomer actor = call.getArgument(3);
                var rows = jdbc.query("select document::text from payment_settlement_quote where review_id=? and request_id=? and payment_id=?",
                        (rs, n) -> SettlementBindingJson.read(rs.getString(1), SettlementQuote.class), reviewId, preparationId, paymentId);
                if (rows.size() != 1 || !rows.getFirst().review().instruction().customerId().equals(actor.customerId()))
                    throw new IllegalStateException("FIXTURE_QUOTE_SCOPE_MISMATCH");
                return rows.getFirst();
            });
            when(owner.executionClaimed(anyString())).thenAnswer(call -> jdbc.queryForObject(
                    "select exists(select 1 from payment_settlement_execution_claim where payment_id=?)", Boolean.class, (String) call.getArgument(0)));
            return owner;
        }
        @Bean SettlementReviewGuard reviewGuard(SettlementQuoteService owner, SettlementSelectionProperties selections) {
            return new SettlementReviewGuard(owner, selections);
        }
        @Bean RecommendationService reviews(PaymentRepository payments, RoutingRecommendationRepository recommendations,
                RoutingDecisionRepository decisions, RoutingAuditRepository audits, OutboxEventRepository outbox,
                RoutingPolicyProperties policies, AccountRepository accounts, BeneficiaryRepository beneficiaries) {
            return new RecommendationService(payments, recommendations, decisions, audits, outbox,
                    new ObjectMapper().findAndRegisterModules(), policies,
                    new DeterministicRoutingAssessmentService(ApprovalFixtures.RULES, ApprovalFixtures.NETWORK, Clock.systemUTC()),
                    accounts, beneficiaries);
        }
        @Bean SettlementExecutionClaimService claims(PaymentRepository payments, RecommendationService reviews,
                RoutingDecisionRepository decisions, CustomerAccessProperties access, JdbcTemplate jdbc) {
            return new SettlementExecutionClaimService(payments, reviews, decisions, access, jdbc);
        }
    }

    @Autowired RecommendationService reviews;
    @Autowired SettlementExecutionClaimService claims;
    @Autowired RoutingPolicyProperties policies;
    @Autowired CustomerAccessProperties access;
    @Autowired SettlementSelectionProperties selections;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    Payment payment;
    AuthorizedCustomer operator, first, second;
    SettlementBinding.Instruction instruction;
    SettlementQuote draft;
    UUID executionId;

    TransactionTemplate tx() { var tx = new TransactionTemplate(transactions); tx.setTimeout(15); return tx; }
    @BeforeEach void seed() {
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var chain = new SettlementNetwork.Chain("besu-local", 20261125, "0x" + "a".repeat(64), SettlementNetwork.Classification.PRIVATE_DEMO);
        var context = new SettlementNetwork.Context(new SettlementNetwork.Token(chain, "0x" + "b".repeat(40), "DEMOUSD", 6, "0x" + "c".repeat(64)),
                new SettlementNetwork.NativeAsset(chain, "BESU_GAS", 18), "0x" + "d".repeat(40), SettlementNetwork.FeeModel.EXECUTION_ONLY, "besu-demo-v1");
        tx().executeWithoutResult(s -> {
            var customer = new Customer(); customer.setName("Fictional private approval customer"); customer.setCustomerType("CORPORATE");
            em.persist(customer); em.flush();
            var account = new Account(); account.setCustomerId(customer.getCustomerId()); account.setCurrency("USD");
            account.setAccountType("DEMO_SIMULATED_USD"); account.setBalance(new BigDecimal("1000")); account.setStatus("ACTIVE"); em.persist(account);
            var beneficiary = new Beneficiary(); beneficiary.setCustomerId(customer.getCustomerId()); beneficiary.setName("Fictional private supplier");
            beneficiary.setCountry("USA"); beneficiary.setCurrency("USD"); beneficiary.setWalletAddress("0x" + "e".repeat(40)); beneficiary.setStatus("ACTIVE");
            em.persist(beneficiary); em.flush();
            var customerId = customer.getCustomerId();
            operator = new AuthorizedCustomer(customerId, "operator"); first = new AuthorizedCustomer(customerId, "first"); second = new AuthorizedCustomer(customerId, "second");
            var policy = new CustomerRoutingPolicy("besu-customer-v1", Set.of(BeneficiaryOutcome.TOKEN_WALLET), Set.of("SIMULATED-BESU-DEMO"), new BigDecimal("3"), 2);
            var body = new PaymentIntentRequest(BeneficiaryOutcome.TOKEN_WALLET, now.plusSeconds(3600), new BigDecimal("3"), SupportedCurrency.USD,
                    RoutingPriority.LOWEST_COST, Set.of(), true, new SettlementSelection("besu-local", context.token().contract(), new BigDecimal("5")));
            payment = new Payment(); payment.setPaymentId("PAY-" + UUID.randomUUID()); payment.setCorrelationId("CORR-" + UUID.randomUUID());
            payment.setSenderAccountId(account.getAccountId()); payment.setBeneficiaryId(beneficiary.getBeneficiaryId()); payment.setAmount(new BigDecimal("5"));
            payment.setSourceCurrency("USD"); payment.setDestinationCurrency("USD"); payment.setPaymentRail("STABLECOIN"); payment.setPurpose("SUPPLIER_INVOICE");
            payment.setStatus("COMPLIANCE_APPROVED"); payment.setIdempotencyKey(UUID.randomUUID().toString()); payment.setRequestFingerprint("a".repeat(64));
            payment.setIntent(new PaymentIntentSnapshot(UUID.randomUUID(), 1, customerId, operator.actorId(), now, SettlementAsset.DEMOUSD, true, false,
                    body, policy, new ConfirmedSettlement(context, payment.getAmount(), beneficiary.getWalletAddress())));
            em.persist(payment); em.flush();
            instruction = ExplicitSettlementService.instruction(payment);
            policies.setPolicies(Map.of(customerId, policy));
            access.setMemberships(Map.of("operator", new Membership(customerId, Set.of(Role.PAYMENT_OPERATOR)),
                    "first", new Membership(customerId, Set.of(Role.PAYMENT_APPROVER)), "second", new Membership(customerId, Set.of(Role.PAYMENT_APPROVER))));
            selections.setAllowedNetworks(Map.of(customerId, Set.of("besu-local")));
            selections.setNetworks(List.of(new SettlementNetworkRegistry.Entry(context, true)));
            new SettlementBindingStore(jdbc).captureInstruction(instruction);
            draft = persistDraft(now);
        });
        executionId = UUID.randomUUID();
    }
    SettlementQuote persistDraft(Instant now) {
        var request = new SettlementPreparation.Request(UUID.randomUUID(), instruction, now);
        var observation = new SettlementObservation(instruction, 100, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1e-18"), now, now.plusSeconds(15));
        var availability = new SettlementAvailability(new SettlementPreparation.Evidence(request, observation), BigDecimal.ZERO, BigDecimal.ZERO, now);
        var terms = new SettlementQuote.Terms("besu-quote-v1", "SIMULATED-BESU-DEMO", BigDecimal.ONE, 900, BigDecimal.ONE);
        var quote = new SettlementQuote(new SettlementBinding.Review(UUID.randomUUID(), 1, request.requestId(), UUID.randomUUID(), 1,
                terms.providerId(), instruction, terms.simulatedFeeUsd(), terms.reserve(observation), now, terms.expiry(instruction, now)), availability, terms);
        jdbc.update("insert into payment_settlement_preparation(request_id,instruction_id,payment_id,correlation_id,actor_id,document,fingerprint) values (?,?,?,?,'operator',cast(? as jsonb),?)",
                request.requestId(), instruction.intentId(), instruction.paymentId(), instruction.correlationId(), SettlementBindingJson.write(request), SettlementBindingJson.fingerprint(request));
        jdbc.update("insert into payment_settlement_quote(review_id,quote_id,request_id,instruction_id,payment_id,correlation_id,actor_id,document,fingerprint) values (?,?,?,?,?,?,'operator',cast(? as jsonb),?)",
                quote.review().recommendationId(), quote.review().quoteId(), request.requestId(), instruction.intentId(), instruction.paymentId(), instruction.correlationId(),
                SettlementBindingJson.write(quote), SettlementBindingJson.fingerprint(quote));
        return quote;
    }
    RecommendationView propose() { return reviews.proposeSettlement(payment.getPaymentId(), draft.review().preparationId(), draft.review().recommendationId(), operator); }
    void vote(RecommendationView r, AuthorizedCustomer actor) {
        var q = r.document().settlementQuote().review();
        reviews.acceptQuote(payment.getPaymentId(), r.recommendationId(), r.version(), q.quoteId(), q.quoteVersion(), actor);
        reviews.approve(payment.getPaymentId(), r.recommendationId(), r.version(), q.quoteId(), q.quoteVersion(), actor);
    }
    RecommendationView approved() { var r = propose(); vote(r, first); vote(r, second); return r; }
    SettlementExecutionClaim claim(RecommendationView r, UUID id, AuthorizedCustomer actor) {
        var q = r.document().settlementQuote().review();
        return claims.claim(payment.getPaymentId(), id, r.recommendationId(), r.version(), q.quoteId(), q.quoteVersion(), actor);
    }
    int count(String table) {
        if (!Set.of("payment_settlement_execution_claim", "payment_execution", "routing_audit").contains(table)) throw new IllegalArgumentException();
        String filter = table.equals("routing_audit") ? " and action='SETTLEMENT_EXECUTION_CLAIMED'" : "";
        return jdbc.queryForObject("select count(*) from " + table + " where payment_id=?" + filter, Integer.class, payment.getPaymentId());
    }
    void oneClaim() { assertThat(count("payment_settlement_execution_claim")).isOne(); assertThat(count("routing_audit")).isOne(); assertThat(count("payment_execution")).isZero(); }
    void concurrently(Runnable firstTask, Runnable secondTask) throws Exception {
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> { await(start); firstTask.run(); });
            var two = pool.submit(() -> { await(start); secondTask.run(); });
            start.countDown(); one.get(20, TimeUnit.SECONDS); two.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
    static void await(CountDownLatch start) {
        try { if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("START_TIMEOUT"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @Test void actualApprovedJpaReviewAndDecisionsProduceOneJdbcClaimAndHistoricalReplay() {
        var r = approved(); var captured = claim(r, executionId, operator);
        assertThat(captured.approvals()).extracting(SettlementExecutionClaim.Approval::actorId).containsExactly("first", "second");
        assertThat(claim(r, executionId, operator)).isEqualTo(captured); oneClaim();
        assertThat(jdbc.queryForObject("select status from payment where payment_id=?", String.class, payment.getPaymentId())).isEqualTo("COMPLIANCE_APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from routing_decision where recommendation_id=?", Integer.class, r.recommendationId())).isEqualTo(4);
    }
    @Test void proposalApprovalsAndClaimCanShareOneOuterJpaJdbcTransaction() {
        tx().executeWithoutResult(s -> { var r = approved(); claim(r, executionId, operator); oneClaim(); });
        oneClaim();
    }
    @Test void outerRollbackRemovesReviewDecisionsEventsClaimAndAudit() {
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            var r = approved(); claim(r, executionId, operator); throw new IllegalStateException("OUTER_ROLLBACK");
        })).hasMessage("OUTER_ROLLBACK");
        assertThat(count("payment_settlement_execution_claim")).isZero(); assertThat(count("routing_audit")).isZero();
        for (var table : List.of("routing_recommendation", "payment_outbox_event")) {
            String key = table.equals("routing_recommendation") ? "payment_id" : "aggregate_id";
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where " + key + "=?", Integer.class, payment.getPaymentId())).isZero();
        }
    }
    @Test void claimAuditFailureRollsBackSecondApprovalInTheSameTransaction() {
        var r = propose(); vote(r, first);
        jdbc.execute("alter table routing_audit add constraint approval_claim_test_failure check (action <> 'SETTLEMENT_EXECUTION_CLAIMED') not valid");
        try {
            assertThatThrownBy(() -> tx().executeWithoutResult(s -> { vote(r, second); claim(r, executionId, operator); })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("approval_claim_test_failure");
            assertThat(count("payment_settlement_execution_claim")).isZero();
            assertThat(jdbc.queryForObject("select count(*) from routing_decision where recommendation_id=? and actor_id='second'", Integer.class, r.recommendationId())).isZero();
            assertThat(reviews.get(payment.getPaymentId(), r.recommendationId(), operator).approvalCount()).isOne();
        } finally { jdbc.execute("alter table routing_audit drop constraint approval_claim_test_failure"); }
        vote(r, second); claim(r, executionId, operator); oneClaim();
    }
    @Test void concurrentActualServiceReplayLeavesOneClaimAndAudit() throws Exception {
        var r = approved(); concurrently(() -> claim(r, executionId, operator), () -> claim(r, executionId, operator)); oneClaim();
    }
    @Test void concurrentConflictingKeysCannotOwnTheSameApprovedPayment() throws Exception {
        var r = approved(); var wins = new AtomicInteger(); var conflicts = new AtomicInteger();
        java.util.function.Consumer<UUID> attempt = id -> {
            try { claim(r, id, operator); wins.incrementAndGet(); }
            catch (ApprovalConflictException e) { assertThat(e).extracting("errorCode").isEqualTo("SETTLEMENT_EXECUTION_CLAIM_CONFLICT"); conflicts.incrementAndGet(); }
        };
        concurrently(() -> attempt.accept(executionId), () -> attempt.accept(UUID.randomUUID()));
        assertThat(wins.get()).isOne(); assertThat(conflicts.get()).isOne(); oneClaim();
    }
    @Test void oneActualApproverCannotCreateClaim() {
        var r = propose(); vote(r, first);
        assertThatThrownBy(() -> claim(r, executionId, operator)).isInstanceOf(ApprovalConflictException.class);
        assertThat(count("payment_settlement_execution_claim")).isZero();
    }
    @Test void revokedApproverAndForeignOperatorCannotCreateClaims() {
        var r = approved(); var members = new HashMap<>(access.getMemberships()); members.remove("second"); access.setMemberships(members);
        assertThatThrownBy(() -> claim(r, executionId, operator)).hasMessageContaining("SETTLEMENT_EXECUTION_ROLE_REQUIRED");
        var foreign = new AuthorizedCustomer(UUID.randomUUID(), "foreign"); members.put("foreign", new Membership(foreign.customerId(), Set.of(Role.PAYMENT_OPERATOR))); access.setMemberships(members);
        assertThatThrownBy(() -> claim(r, executionId, foreign)).hasMessageContaining("CUSTOMER_SCOPE_MISMATCH");
        assertThat(count("payment_settlement_execution_claim")).isZero();
    }
    @Test void canonicalVersionTwoCanClaimWithoutRewritingVersionOneDraft() {
        var old = propose();
        draft = tx().execute(s -> persistDraft(Instant.now().truncatedTo(ChronoUnit.MICROS)));
        var current = approved(); assertThat(current.version()).isEqualTo(2);
        assertThat(current.document().settlementQuote().review().version()).isOne();
        var c = claim(current, executionId, operator); assertThat(c.execution().review().version()).isEqualTo(2); oneClaim();
        assertThat(reviews.get(payment.getPaymentId(), old.recommendationId(), operator).state()).isEqualTo(RoutingRecommendation.State.SUPERSEDED);
    }
}