package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.intent.PaymentIntentSnapshot;
import com.cognizant.stablecoins.payment.support.IntentFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static com.cognizant.stablecoins.payment.routing.SimulatedRoutingScenarios.Scenario.*;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true) @Transactional(propagation=Propagation.NOT_SUPPORTED)
class AgentRunPersistenceIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired EntityManager em;@Autowired PlatformTransactionManager manager;@Autowired RoutingAgentRunRepository runs;@Autowired JdbcTemplate jdbc;
    UUID customer;String paymentId;UUID recommendationId;TransactionTemplate tx;
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);tx.executeWithoutResult(s->{
        var c=new Customer();c.setName("Fictional agent customer");c.setCustomerType("CORPORATE");em.persist(c);em.flush();customer=c.getCustomerId();
        var account=IntentFixtures.account();account.setCustomerId(customer);account.setAccountType("OPERATING");account.setBalance(new java.math.BigDecimal("100000"));em.persist(account);
        var beneficiary=IntentFixtures.beneficiary();beneficiary.setCustomerId(customer);beneficiary.setName("Fictional supplier");beneficiary.setCountry("IND");em.persist(beneficiary);em.flush();
        var p=new Payment();paymentId="PAY-"+UUID.randomUUID();p.setPaymentId(paymentId);p.setCorrelationId("CORR-"+UUID.randomUUID());p.setSenderAccountId(account.getAccountId());p.setBeneficiaryId(beneficiary.getBeneficiaryId());p.setAmount(new java.math.BigDecimal("10000"));p.setSourceCurrency("USD");p.setDestinationCurrency("INR");p.setPaymentRail("AUTO");p.setPurpose("SUPPLIER_INVOICE");p.setStatus("COMPLIANCE_APPROVED");p.setIdempotencyKey(UUID.randomUUID().toString());p.setRequestFingerprint("a".repeat(64));em.persist(p);em.flush();
        var r=new RoutingRecommendation();recommendationId=UUID.randomUUID();r.id=recommendationId;r.paymentId=paymentId;r.correlationId=p.getCorrelationId();r.customerId=customer;r.version=1;r.requiredApprovals=2;r.state=RoutingRecommendation.State.PROPOSED;r.document=material(NORMAL,1).document();r.createdAt=Instant.now();r.expiresAt=Instant.now().plusSeconds(100);em.persist(r);
    });}
    RoutingAgentRun row(String key){var r=new RoutingAgentRun();r.id=UUID.randomUUID();r.paymentId=paymentId;r.correlationId="CORR-AGENT";r.customerId=customer;r.actorId="operator";r.recommendationId=recommendationId;r.idempotencyHash=AgentInput.hash(key);r.fingerprint="a".repeat(64);r.configuredModel="test-model";r.configuredProvider="OPENAI";r.inferenceRegion="PROVIDER_MANAGED";r.promptVersion=RoutingAgent.PROMPT_VERSION;r.status=RoutingAgentRun.Status.RUNNING;r.createdAt=Instant.now();r.deadlineAt=r.createdAt.plusSeconds(90);return r;}
    @Test void migrationAndJsonbReportRoundTrip(){var r=row("roundtrip");var m=material(NORMAL,1);r.status=RoutingAgentRun.Status.COMPLETED;r.finishedAt=Instant.now();
        r.report=new AgentReport(SEED,1,m.document().evidence().snapshotId(),1,"test-v1","rules-v1","test-model",RoutingAgent.PROMPT_VERSION,"OPENAI","PROVIDER_MANAGED",NOW,NOW.plusSeconds(100),answer(m,"SIMULATED-A"),List.of("simulated"),m.document().assessment().candidates(),List.of(),List.of("resp-test"),12,8,1,new java.math.BigDecimal("0.001"),"CONFIGURED_TOKEN_RATES_NOT_PROVIDER_BILLING","CAPTURED_SERVER_EVIDENCE_NOT_LIVE_POLL");
        tx.executeWithoutResult(s->runs.saveAndFlush(r));var loaded=tx.execute(s->runs.findById(r.id).orElseThrow().report);assertThat(loaded).isEqualTo(r.report);}
    @Test void onlyOneActiveRunPerPayment(){tx.executeWithoutResult(s->runs.saveAndFlush(row("one")));
        assertThatThrownBy(()->tx.executeWithoutResult(s->runs.saveAndFlush(row("two")))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
    @Test void terminalReplayKeyIsStillUnique(){var r=row("same-key");r.status=RoutingAgentRun.Status.FAILED;r.finishedAt=Instant.now();r.failureCode="AGENT_PROVIDER_UNAVAILABLE";tx.executeWithoutResult(s->runs.saveAndFlush(r));
        assertThatThrownBy(()->tx.executeWithoutResult(s->runs.saveAndFlush(row("same-key")))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
    @Test void completedRunCannotOmitValidatedReport(){var r=row("invalid");r.status=RoutingAgentRun.Status.COMPLETED;r.finishedAt=Instant.now();assertThatThrownBy(()->tx.executeWithoutResult(s->runs.saveAndFlush(r))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
}
