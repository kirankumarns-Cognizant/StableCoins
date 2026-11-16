package com.cognizant.stablecoins.payment.approval;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.cognizant.stablecoins.payment.routing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.cognizant.stablecoins.payment.approval.ApprovalFixtures.*;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true) @Import(RecommendationPersistenceIT.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RecommendationPersistenceIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @TestConfiguration static class Config {
        @Bean RoutingPolicyProperties policies(){return new RoutingPolicyProperties();}
        @Bean RecommendationService approvals(PaymentRepository p,RoutingRecommendationRepository r,RoutingDecisionRepository d,
                RoutingAuditRepository a,OutboxEventRepository o,RoutingPolicyProperties policy,AccountRepository accounts,BeneficiaryRepository beneficiaries) {
            var clock=Clock.fixed(NOW,ZoneOffset.UTC);
            return new RecommendationService(p,r,d,a,o,new ObjectMapper().findAndRegisterModules(),policy,
                new DeterministicRoutingAssessmentService(RULES,NETWORK,clock),accounts,beneficiaries,clock);
        }
    }
    @Autowired RecommendationService service; @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em; @Autowired JdbcTemplate jdbc; @Autowired RoutingPolicyProperties policies;
    Payment payment;AssessmentContext context;RoutingEvidence.Snapshot evidence;RecommendationDocument.Quote quote;AuthorizedCustomer first,second;
    @BeforeEach void setup() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            for(String table:List.of("payment_flow_preparation","routing_decision","routing_audit","routing_recommendation","payment_outbox_event","payment_status_history","payment","beneficiary","account","customer")) jdbc.execute("DELETE FROM "+table);
            Customer c=new Customer();c.setName("Fictional approval customer");c.setCustomerType("CORPORATE");em.persist(c);em.flush();
            var a=com.cognizant.stablecoins.payment.support.IntentFixtures.account();a.setCustomerId(c.getCustomerId());a.setAccountType("OPERATING");a.setBalance(new java.math.BigDecimal("100000"));em.persist(a);
            var b=com.cognizant.stablecoins.payment.support.IntentFixtures.beneficiary();b.setCustomerId(c.getCustomerId());b.setName("Fictional supplier");b.setCountry("IND");em.persist(b);em.flush();
            payment=payment();payment.setSenderAccountId(a.getAccountId());payment.setBeneficiaryId(b.getBeneficiaryId());var i=payment.getIntent();
            payment.setIntent(new PaymentIntentSnapshot(i.intentId(),i.intentVersion(),c.getCustomerId(),i.confirmedBy(),i.confirmedAt(),i.settlementAsset(),true,true,i.instruction(),i.policy()));
            policies.setPolicies(Map.of(c.getCustomerId(),i.policy()));em.persist(payment);em.flush();
            first=new AuthorizedCustomer(c.getCustomerId(),"first");second=new AuthorizedCustomer(c.getCustomerId(),"second");
        });
        context=context(payment);evidence=evidence(context);quote=quote(context);
    }
    RecommendationView propose(){return service.propose(UUID.randomUUID(),first,context,evidence,quote);}
    void accept(RecommendationView r,AuthorizedCustomer who){service.acceptQuote(payment.getPaymentId(),r.recommendationId(),r.version(),quote.quoteId(),1,who);}
    RecommendationView approve(RecommendationView r,AuthorizedCustomer who){return service.approve(payment.getPaymentId(),r.recommendationId(),r.version(),quote.quoteId(),1,who);}
    void concurrently(Runnable a,Runnable b) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Future<?> one=pool.submit(()->{try{start.await();a.run();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}});
            Future<?> two=pool.submit(()->{try{start.await();b.run();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}});
            start.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);
        }
    }
    @Test void capturedProposalCanBeLinkedThroughJdbcBeforeOuterTransactionCommits(){
        UUID id=UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.update("insert into payment_flow_preparation(request_id,payment_id,correlation_id,actor_id,request_document,status) values (?,?,?,'first','{}'::jsonb,'PENDING')",id,payment.getPaymentId(),payment.getCorrelationId());
            var r=service.proposeCaptured(id,first,context,evidence,List.of(quote));
            // JdbcTemplate does not trigger Hibernate AUTO flush. PostgreSQL checks this FK immediately.
            jdbc.update("update payment_flow_preparation set status='READY',recommendation_id=? where request_id=?",r.recommendationId(),id);
            assertThat(jdbc.queryForObject("select count(*) from payment_flow_preparation p join routing_recommendation r on r.recommendation_id=p.recommendation_id where p.request_id=? and p.status='READY'",Integer.class,id)).isEqualTo(1);
        });
    }
    @Test void failedPreparationLinkRollsBackFlushedRecommendationAndAudit(){
        UUID id=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            service.proposeCaptured(id,first,context,evidence,List.of(quote));
            assertThat(jdbc.queryForObject("select count(*) from routing_recommendation where recommendation_id=?",Integer.class,id)).isEqualTo(1);
            // An invalid payment causes an immediate FK failure after the JPA flush.
            jdbc.update("insert into payment_flow_preparation(request_id,payment_id,correlation_id,actor_id,request_document,status,recommendation_id) values (?,'PAY-MISSING','CORR-MISSING','first','{}'::jsonb,'READY',?)",UUID.randomUUID(),id);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from routing_recommendation where recommendation_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from routing_audit where recommendation_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from payment_outbox_event",Integer.class)).isZero();
    }
    @Test void migrationAndJsonbEvidenceRoundTrip(){var r=propose();var loaded=service.get(payment.getPaymentId(),r.recommendationId(),first);assertThat(loaded.document()).isEqualTo(r.document());assertThat(loaded.requiredApprovals()).isEqualTo(2);}
    @Test void concurrentDuplicateApprovalsCreateOneVote() throws Exception {var r=propose();accept(r,first);concurrently(()->approve(r,first),()->approve(r,first));
        var loaded=service.get(payment.getPaymentId(),r.recommendationId(),first);assertThat(loaded.approvalCount()).isEqualTo(1);assertThat(loaded.state()).isEqualTo(RoutingRecommendation.State.PROPOSED);
        assertThat(jdbc.queryForObject("select count(*) from routing_audit where action='APPROVED'",Integer.class)).isEqualTo(1);}
    @Test void concurrentDistinctApprovalsReachThresholdOnce() throws Exception {var r=propose();accept(r,first);accept(r,second);concurrently(()->approve(r,first),()->approve(r,second));
        assertThat(service.get(payment.getPaymentId(),r.recommendationId(),first).state()).isEqualTo(RoutingRecommendation.State.APPROVED);
        assertThat(jdbc.queryForObject("select count(*) from payment_outbox_event where event_type='RoutingRecommendationApproved'",Integer.class)).isEqualTo(1);}
    @Test void concurrentProposalsLeaveOneActiveVersion() throws Exception {concurrently(()->propose(),()->propose());
        assertThat(jdbc.queryForObject("select count(*) from routing_recommendation where state='PROPOSED'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select max(version) from routing_recommendation",Integer.class)).isEqualTo(2);}
    @Test void outboxFailureRollsBackApprovalAndDecision() {var r=propose();accept(r,first);approve(r,first);accept(r,second);
        jdbc.execute("alter table payment_outbox_event add constraint reject_approved_event check (event_type <> 'RoutingRecommendationApproved')");
        try {assertThatThrownBy(()->approve(r,second)).isInstanceOf(RuntimeException.class);
            var loaded=service.get(payment.getPaymentId(),r.recommendationId(),first);assertThat(loaded.state()).isEqualTo(RoutingRecommendation.State.PROPOSED);assertThat(loaded.approvalCount()).isEqualTo(1);
        } finally {jdbc.execute("alter table payment_outbox_event drop constraint reject_approved_event");}}
}
