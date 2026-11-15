package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.model.Payment;
import com.cognizant.stablecoins.payment.repository.PaymentRepository;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.exception.PaymentAccessException;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import org.junit.jupiter.api.*;
import org.springframework.core.task.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AgentRunServiceTest {
    RoutingAgentRunRepository runs=mock(RoutingAgentRunRepository.class);PaymentRepository payments=mock(PaymentRepository.class);
    AgentEvidenceSource source=mock(AgentEvidenceSource.class);RoutingAgent agent=mock(RoutingAgent.class);
    TaskExecutor executor=mock(TaskExecutor.class);PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    CustomerAccessProperties access=new CustomerAccessProperties();AgentSettings limits=settings();AgentRunService service;
    Map<UUID,RoutingAgentRun> rows=new LinkedHashMap<>();Runnable task;
    @BeforeEach void setup(){
        access.setMemberships(Map.of(ACTOR.actorId(),new CustomerAccessProperties.Membership(ACTOR.customerId(),Set.of(CustomerAccessProperties.Role.PAYMENT_OPERATOR))));
        when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        var p=new Payment();p.setPaymentId("PAY-AGENT");p.setCorrelationId("CORR-AGENT");p.setIntent(material(com.cognizant.stablecoins.payment.routing.SimulatedRoutingScenarios.Scenario.NORMAL,1).document().intent());
        when(payments.lockByPaymentId("PAY-AGENT")).thenReturn(Optional.of(p));
        when(runs.findByPaymentIdAndActorIdAndIdempotencyHash(anyString(),anyString(),anyString())).thenAnswer(i->rows.values().stream().filter(r->r.paymentId.equals(i.getArgument(0))&&r.actorId.equals(i.getArgument(1))&&r.idempotencyHash.equals(i.getArgument(2))).findFirst());
        when(runs.findByPaymentIdAndStatus(anyString(),any())).thenAnswer(i->rows.values().stream().filter(r->r.paymentId.equals(i.getArgument(0))&&r.status==i.getArgument(1)).toList());
        when(runs.countByPaymentId(anyString())).thenAnswer(i->(long)rows.size());
        when(runs.saveAndFlush(any())).thenAnswer(i->{RoutingAgentRun r=i.getArgument(0);rows.put(r.id,r);return r;});
        when(runs.save(any())).thenAnswer(i->i.getArgument(0));when(runs.lockById(any())).thenAnswer(i->Optional.ofNullable(rows.get(i.getArgument(0))));
        doAnswer(i->{task=i.getArgument(0);return null;}).when(executor).execute(any());
        service=new AgentRunService(runs,payments,source,agent,limits,provider(),access,executor,manager);
    }
    AgentRunView start(){return service.start("PAY-AGENT",SEED,ACTOR,"idem-test","Explain the confirmed payment");}
    @Test void replayCreatesOneRunAndSchedulesInferenceOnce(){var first=start();var replay=start();assertThat(replay.runId()).isEqualTo(first.runId());
        verify(executor,times(1)).execute(any());verifyNoInteractions(agent);assertThat(rows.values().iterator().next().idempotencyHash).hasSize(64).isNotEqualTo("idem-test");}
    @Test void sameKeyDifferentInstructionConflicts(){start();assertThatThrownBy(()->service.start("PAY-AGENT",SEED,ACTOR,"idem-test","different"))
        .hasMessage("AGENT_IDEMPOTENCY_CONFLICT");verify(executor,times(1)).execute(any());}
    @Test void secondActiveRunCannotSpendAgain(){start();assertThatThrownBy(()->service.start("PAY-AGENT",SEED,ACTOR,"other-key","another"))
        .hasMessage("AGENT_RUN_IN_PROGRESS");verify(executor,times(1)).execute(any());}
    @Test void workerFailuresPersistOnlySanitizedCode(){var view=start();when(agent.run(anyString(),any(),any(),anyString(),any())).thenThrow(new IllegalStateException("sk-secret-test"));
        task.run();var result=service.get("PAY-AGENT",SEED,view.runId(),ACTOR);assertThat(result.status()).isEqualTo(RoutingAgentRun.Status.FAILED);
        assertThat(result.failureCode()).isEqualTo("AGENT_INTERNAL_FAILURE");assertThat(JSON.valueToTree(result).toString()).doesNotContain("sk-secret-test","Explain the confirmed");}
    @Test void expiredRunCannotBeOverwrittenByLateCompletion(){var view=start();var r=rows.get(view.runId());r.deadlineAt=Instant.now().minusSeconds(1);
        service.finish(r.id,null,"OTHER_FAILURE");assertThat(r.status).isEqualTo(RoutingAgentRun.Status.TIMED_OUT);assertThat(r.failureCode).isEqualTo("AGENT_DEADLINE_EXCEEDED");}
    @Test void crashedRunReplayExpiresWithoutRescheduling(){var first=start();rows.get(first.runId()).deadlineAt=Instant.now().minusSeconds(1);
        assertThat(start().status()).isEqualTo(RoutingAgentRun.Status.TIMED_OUT);verify(executor,times(1)).execute(any());}
    @Test void capacityFailureIsDurableAndNotAutomaticallyRetried(){doThrow(new TaskRejectedException("busy")).when(executor).execute(any());
        assertThatThrownBy(this::start).hasMessage("AGENT_CAPACITY_EXHAUSTED");assertThat(start().failureCode()).isEqualTo("AGENT_CAPACITY_EXHAUSTED");verify(executor,times(1)).execute(any());}
    @Test void membershipRevocationBlocksRunReads(){var first=start();access.setMemberships(Map.of());assertThatThrownBy(()->service.get("PAY-AGENT",SEED,first.runId(),ACTOR)).isInstanceOf(PaymentAccessException.class);}
    @Test void anotherCustomerCannotReadAudit(){var first=start();UUID customer=UUID.randomUUID();access.setMemberships(Map.of("other",new CustomerAccessProperties.Membership(customer,Set.of(CustomerAccessProperties.Role.PAYMENT_OPERATOR))));
        assertThatThrownBy(()->service.get("PAY-AGENT",SEED,first.runId(),new AuthorizedCustomer(customer,"other"))).isInstanceOf(PaymentAccessException.class);}
    @Test void totalPaymentRunBudgetLimitsRepeatedRequests(){start();rows.values().forEach(r->r.status=RoutingAgentRun.Status.FAILED);limits.setMaximumRunsPerPayment(1);
        assertThatThrownBy(()->service.start("PAY-AGENT",SEED,ACTOR,"key2","explain")).hasMessage("AGENT_PAYMENT_RUN_LIMIT");}
}
