package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.repository.PaymentRepository;
import com.cognizant.stablecoins.payment.settlement.ExplicitSettlementService;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.platform.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Internal owner commands. Authorization accepts only server-verified identity, never browser-supplied roles. */
@Service
@ConditionalOnProperty(name={"app.flow.enabled","app.besu.recovery.enabled"},havingValue="true")
public class BesuPaymentFailureService {
    private final PaymentRepository payments;private final PaymentFlowService flow;private final BesuRevertEvidence reads;
    private final BesuPaymentFailureStore store;private final CustomerAccessProperties access;
    @org.springframework.beans.factory.annotation.Autowired
    public BesuPaymentFailureService(PaymentRepository payments,PaymentFlowService flow,JdbcTemplate jdbc,ObjectMapper mapper,Environment env,CustomerAccessProperties access) {
        this(payments,flow,new BesuRevertEvidence(jdbc,mapper,env),new BesuPaymentFailureStore(jdbc),access);
    }
    BesuPaymentFailureService(PaymentRepository payments,PaymentFlowService flow,BesuRevertEvidence reads,BesuPaymentFailureStore store,CustomerAccessProperties access) {
        this.payments=payments;this.flow=flow;this.reads=reads;this.store=store;this.access=access;
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public void advance(SettlementBinding.Execution e) {
        var p=payments.lockByPaymentId(e.instruction().paymentId()).orElseThrow();requireInstruction(e,p);
        var prior=store.failure(e);
        if(prior.isPresent()) {if(!"FAILED".equals(p.getStatus()))throw conflict();return;}
        if(!Set.of("FUNDED","BLOCKCHAIN_SUBMITTED").contains(p.getStatus()))throw conflict();
        var proof=reads.current(e); // Conclusive canonical revert, exact funding/allocation and complete actual fees.
        flow.transition(p,"FAILED","Private DEMOUSD transaction reverted; actual gas retained; explicit accounting recovery required");
        flow.command(p,"BesuPaymentFailed",Map.of("executionId",e.executionId(),"txHash",proof.sources().receipt().transactionHash(),
            "sourceHash",proof.sourceHash(),"failureCode","BESU_TRANSACTION_REVERTED"),"1.0");
        payments.flush();store.record(e,proof);
    }
    /** Explicit, source-bound release of the token in-flight CONTROL position only. No USD refund or chain action. */
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public void authorizeRelease(SettlementBinding.Execution e,AuthorizedCustomer actor,String expectedSourceHash) {
        var p=payments.lockByPaymentId(e.instruction().paymentId()).orElseThrow();requireInstruction(e,p);
        var member=actor==null?null:access.getMemberships().get(actor.actorId());
        if(member==null || !member.roles().contains(CustomerMembershipProperties.Role.PAYMENT_APPROVER)
            || !member.customerId().equals(e.instruction().customerId()) || !member.customerId().equals(actor.customerId())
            || actor.actorId().equals(store.fundingActor(e)))throw new AccessFailure("BESU_RECOVERY_APPROVER_REQUIRED",403);
        if(!"FAILED".equals(p.getStatus()))throw conflict();
        var proof=reads.current(e);
        if(expectedSourceHash==null || !proof.sourceHash().equals(expectedSourceHash)
            || !store.failure(e).filter(proof.sourceHash()::equals).isPresent())throw conflict();
        var prior=store.authorized(e);
        if(prior.isPresent()) {if(!prior.get().equals(expectedSourceHash))throw conflict();return;}
        if(proof.released())throw conflict(); // An unacknowledged/manual release cannot acquire retroactive authorization.
        store.authorize(e,expectedSourceHash,actor.actorId());
        flow.command(p,"BesuCompensationAuthorized",Map.of("executionId",e.executionId(),"sourceHash",expectedSourceHash,
            "action",BesuRevertEvidence.ACTION),"1.0");
    }
    private static void requireInstruction(SettlementBinding.Execution e,com.cognizant.stablecoins.payment.model.Payment p) {
        if(!e.instruction().equals(ExplicitSettlementService.instruction(p)) || !e.instruction().correlationId().equals(p.getCorrelationId())
            || !"USD".equals(p.getSourceCurrency()) || !"STABLECOIN".equals(p.getPaymentRail()))throw conflict();
    }
    private static IllegalStateException conflict(){return new IllegalStateException("BESU_PAYMENT_FAILURE_CONFLICT");}
}
