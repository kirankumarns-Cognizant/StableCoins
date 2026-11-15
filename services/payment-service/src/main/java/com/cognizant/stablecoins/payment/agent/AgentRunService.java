package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.repository.PaymentRepository;
import com.cognizant.stablecoins.payment.exception.*;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
/** Durable idempotent admission; no database transaction is held during model inference. */
@Service
public class AgentRunService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AgentRunService.class);
    private final RoutingAgentRunRepository runs;private final PaymentRepository payments;private final AgentEvidenceSource source;
    private final RoutingAgent agent;private final AgentSettings settings;private final AgentProviderProperties provider;
    private final CustomerAccessProperties access;private final TaskExecutor executor;private final TransactionTemplate tx;
    public AgentRunService(RoutingAgentRunRepository runs,PaymentRepository payments,AgentEvidenceSource source,RoutingAgent agent,
            AgentSettings settings,AgentProviderProperties provider,CustomerAccessProperties access,
            @Qualifier("routingAgentExecutor")TaskExecutor executor,PlatformTransactionManager manager){
        this.runs=runs;this.payments=payments;this.source=source;this.agent=agent;this.settings=settings;this.provider=provider;
        this.access=access;this.executor=executor;this.tx=new TransactionTemplate(manager);
    }
    private record Claim(AgentRunView view,boolean created){ }
    public AgentRunView start(String paymentId,UUID recommendationId,AuthorizedCustomer actor,String key,String instruction){
        authorize(actor);settings.require(actor.customerId());provider.validate();
        if(key==null||!key.matches("[A-Za-z0-9._:-]{1,200}"))throw new AgentFailure("AGENT_INVALID_IDEMPOTENCY_KEY");
        String redacted=AgentInput.redact(instruction);
        if(redacted.isBlank())throw new AgentFailure("AGENT_INVALID_INSTRUCTION");
        String keyHash=AgentInput.hash(key),fingerprint=AgentInput.hash(recommendationId+"\n"+instruction);
        Claim claim=tx.execute(s->{
            var payment=payments.lockByPaymentId(paymentId).orElseThrow(()->new PaymentNotFoundException(paymentId));
            if(payment.getIntent()==null||!payment.getIntent().customerId().equals(actor.customerId()))throw denied();
            var replay=runs.findByPaymentIdAndActorIdAndIdempotencyHash(paymentId,actor.actorId(),keyHash);
            if(replay.isPresent()){
                var r=runs.lockById(replay.get().id).orElseThrow();if(!r.fingerprint.equals(fingerprint))throw new AgentFailure("AGENT_IDEMPOTENCY_CONFLICT");
                expire(r);return new Claim(AgentRunView.of(r),false);
            }
            source.read(paymentId,recommendationId,actor,false);
            for(var pending:runs.findByPaymentIdAndStatus(paymentId,RoutingAgentRun.Status.RUNNING)){
                var r=runs.lockById(pending.id).orElseThrow();expire(r);if(r.status==RoutingAgentRun.Status.RUNNING)throw new AgentFailure("AGENT_RUN_IN_PROGRESS");
            }
            runs.flush(); // Release expired partial-unique claims before inserting a replacement.
            if(runs.countByPaymentId(paymentId)>=settings.getMaximumRunsPerPayment())throw new AgentFailure("AGENT_PAYMENT_RUN_LIMIT");
            var r=new RoutingAgentRun();r.id=UUID.randomUUID();r.paymentId=paymentId;r.correlationId=payment.getCorrelationId();
            r.customerId=actor.customerId();r.actorId=actor.actorId();r.recommendationId=recommendationId;r.idempotencyHash=keyHash;r.fingerprint=fingerprint;
            r.configuredModel=provider.getModel();r.configuredProvider=provider.getProvider().name();r.inferenceRegion=provider.getProvider()==AgentProviderProperties.Provider.BEDROCK?provider.getRegion():"PROVIDER_MANAGED";r.promptVersion=RoutingAgent.PROMPT_VERSION;r.status=RoutingAgentRun.Status.RUNNING;
            r.createdAt=Instant.now();r.deadlineAt=r.createdAt.plusMillis(settings.getDeadlineMs());runs.saveAndFlush(r);
            return new Claim(AgentRunView.of(r),true);
        });
        if(!claim.created())return claim.view();
        log.info("service=payment-service event=agent_run_started paymentId={} correlationId={} runId={} status=RUNNING",paymentId,claim.view().correlationId(),claim.view().runId());
        try{executor.execute(()->execute(claim.view(),actor,redacted));}
        catch(org.springframework.core.task.TaskRejectedException e){finish(claim.view().runId(),null,"AGENT_CAPACITY_EXHAUSTED");throw new AgentFailure("AGENT_CAPACITY_EXHAUSTED");}
        return claim.view();
    }
    private void execute(AgentRunView run,AuthorizedCustomer actor,String instruction){
        try{
            if(!run.deadlineAt().isAfter(Instant.now())){finish(run.runId(),null,"AGENT_DEADLINE_EXCEEDED");return;}
            AgentReport report=agent.run(run.paymentId(),run.recommendationId(),actor,instruction,run.deadlineAt());
            finish(run.runId(),report,null);
        }catch(AgentFailure e){finish(run.runId(),null,e.code());}
        catch(PaymentAccessException e){finish(run.runId(),null,"AGENT_ACCESS_REVOKED");}
        catch(Exception e){finish(run.runId(),null,"AGENT_INTERNAL_FAILURE");} // Never persist/log provider messages or supplied instructions.
    }
    void finish(UUID id,AgentReport report,String failure){tx.executeWithoutResult(s->{
        var r=runs.lockById(id).orElseThrow();expire(r);
        if(r.status!=RoutingAgentRun.Status.RUNNING)return;
        r.finishedAt=Instant.now();r.report=report;r.failureCode=failure;
        r.status=report!=null?RoutingAgentRun.Status.COMPLETED:
            "AGENT_DEADLINE_EXCEEDED".equals(failure)?RoutingAgentRun.Status.TIMED_OUT:RoutingAgentRun.Status.FAILED;
        runs.save(r);
    });}
    public AgentRunView get(String paymentId,UUID recommendationId,UUID runId,AuthorizedCustomer actor){
        authorize(actor);
        return tx.execute(s->{var r=runs.lockById(runId).orElseThrow(()->new AgentFailure("AGENT_RUN_NOT_FOUND"));
            if(!r.customerId.equals(actor.customerId())||!r.paymentId.equals(paymentId)||!r.recommendationId.equals(recommendationId))throw denied();
            expire(r);return AgentRunView.of(r);
        });
    }
    private void expire(RoutingAgentRun r){if(r.status==RoutingAgentRun.Status.RUNNING&&!r.deadlineAt.isAfter(Instant.now())){
        r.status=RoutingAgentRun.Status.TIMED_OUT;r.finishedAt=Instant.now();r.failureCode="AGENT_DEADLINE_EXCEEDED";runs.save(r);
    }}
    private void authorize(AuthorizedCustomer actor){
        if(actor==null)throw denied();var m=access.getMemberships().get(actor.actorId());
        if(m==null||!m.customerId().equals(actor.customerId())||!m.roles().contains(CustomerAccessProperties.Role.PAYMENT_OPERATOR))throw denied();
    }
    private PaymentAccessException denied(){return new PaymentAccessException("CUSTOMER_ACCESS_DENIED","Agent run is outside authorized customer scope");}
}
