package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.flow.BesuPaymentFailureService;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.service.PaymentService;
import com.cognizant.stablecoins.payment.exception.PaymentAccessException;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Trusted application facade. Browser supplies identifiers, never chain context, roles or financial evidence. */
@Service
@ConditionalOnProperty(name={"app.flow.enabled","app.besu.application-api.enabled"},havingValue="true")
public class PrivateSettlementApplication {
    private final com.cognizant.stablecoins.payment.repository.PaymentRepository repository;
    private final PaymentService payments; private final JdbcTemplate jdbc;
    private final SettlementQuoteService quotes; private final RecommendationService recommendations;
    private final SettlementExecutionClaimService claims; private final BesuRevertEvidence reverts;
    private final ObjectProvider<BesuPaymentFailureService> recovery; private final CustomerAccessProperties access;
    public PrivateSettlementApplication(PaymentService payments,JdbcTemplate jdbc,SettlementQuoteService quotes,
            RecommendationService recommendations,SettlementExecutionClaimService claims,ObjectMapper mapper,
            Environment env,ObjectProvider<BesuPaymentFailureService> recovery,CustomerAccessProperties access,
            com.cognizant.stablecoins.payment.repository.PaymentRepository repository) {
        this.repository=repository;this.payments=payments;this.jdbc=jdbc;this.quotes=quotes;this.recommendations=recommendations;
        this.claims=claims;this.reverts=new BesuRevertEvidence(jdbc,mapper,env);this.recovery=recovery;this.access=access;
    }
    private SettlementBinding.Instruction instruction(String id,AuthorizedCustomer actor) {
        payments.getPayment(id,actor); // Customer authorization precedes ALL private-owner reads.
        var i=ExplicitSettlementService.instruction(repository.findById(id).orElseThrow());
        if(i==null || i.context().token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
                || !i.customerId().equals(actor.customerId()))throw new ApprovalConflictException("PRIVATE_SETTLEMENT_REQUIRED");
        return i;
    }
    @Transactional
    public RecommendationView propose(String id,UUID preparation,UUID key,AuthorizedCustomer actor) {
        instruction(id,actor);quotes.prepare(id,preparation,key,actor);
        return recommendations.proposeSettlement(id,preparation,key,actor);
    }
    @Transactional
    public SettlementExecutionClaim claim(String id,UUID recommendation,UUID key,int version,UUID quote,int quoteVersion,AuthorizedCustomer actor) {
        instruction(id,actor);return claims.claim(id,key,recommendation,version,quote,quoteVersion,actor);
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public void authorizeRelease(String id,UUID execution,String sourceHash,AuthorizedCustomer actor) {
        var i=instruction(id,actor);
        var own=jdbc.queryForList("select execution_id from payment_settlement_execution_claim where payment_id=?",UUID.class,id);
        if(own.size()!=1 || !own.getFirst().equals(execution))throw new ApprovalConflictException("SETTLEMENT_EXECUTION_CLAIM_CONFLICT");
        var e=reverts.execution(execution);
        if(!e.instruction().equals(i))throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH","Payment is outside customer scope");
        var owner=recovery.getIfAvailable();
        if(owner==null)throw new ApprovalConflictException("BESU_RECOVERY_DISABLED");
        owner.authorizeRelease(e,actor,sourceHash);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> evidence(String id,AuthorizedCustomer actor) {
        var i=instruction(id,actor);var result=new LinkedHashMap<String,Object>();
        result.put("instruction",i);result.put("classification","PRIVATE_DEMO");
        result.put("newPaymentsEnabled",false);result.put("acceptanceGate","BESU_PAYMENT_ACCEPTANCE_PENDING");
        result.put("preparations",rows("select request_id,created_at from payment_settlement_preparation where payment_id=? order by created_at desc,request_id limit 50",id));
        result.put("claims",rows("select execution_id,claimed_at from payment_settlement_execution_claim where payment_id=?",id));
        // Explicit field lists: signed payloads, signing paths and credentials never enter a read model.
        result.put("submission",rows("select s.execution_id,s.status,s.tx_hash,s.nonce,s.gas_limit,s.gas_price from besu_signed_submission s join payment_settlement_execution_claim c using(execution_id) where c.payment_id=?",id));
        result.put("receipt",rows("select r.execution_id,r.tx_hash,r.block_hash,r.block_number,r.success,r.gas_used,r.gas_price,r.total_fee_wei,r.finality_policy from besu_final_receipt r join payment_settlement_execution_claim c using(execution_id) where c.payment_id=?",id));
        result.put("closure",rows("select execution_id,report_id,source_hash,completed_at from payment_besu_closure where payment_id=?",id));
        var failures=rows("select execution_id,source_hash,tx_hash,failure_code from payment_besu_failure where payment_id=?",id);
        var grants=rows("select execution_id,source_hash,actor_id,action from payment_besu_recovery_authorization where payment_id=?",id);
        result.put("failure",failures);result.put("authorization",grants);
        result.put("compensation",rows("select execution_id,source_hash,actor_id,original_journal,release_journal from besu_ledger_compensation where payment_id=?",id));
        result.put("recovery",recoveryView(i,actor,failures,grants));
        return result;
    }
    private Map<String,Object> recoveryView(SettlementBinding.Instruction i,AuthorizedCustomer actor,List<Map<String,Object>> failures,List<Map<String,Object>> grants) {
        if(recovery.getIfAvailable()==null || failures.size()!=1 || !grants.isEmpty())return Map.of("eligible",false,"reason","NO_UNAUTHORIZED_RECOVERY_PENDING");
        var member=access.getMemberships().get(actor.actorId());
        if(member==null || !member.customerId().equals(actor.customerId()) || !member.roles().contains(CustomerAccessProperties.Role.PAYMENT_APPROVER))
            return Map.of("eligible",false,"reason","BESU_RECOVERY_APPROVER_REQUIRED");
        var execution=(UUID)failures.getFirst().get("execution_id");var e=reverts.execution(execution);
        if(!e.instruction().equals(i))throw new IllegalStateException("BESU_RECOVERY_BINDING_CHANGED");
        var operator=jdbc.queryForObject("select actor_id from payment_besu_funding where execution_id=?",String.class,execution);
        if(actor.actorId().equals(operator))return Map.of("eligible",false,"reason","INDEPENDENT_APPROVER_REQUIRED");
        try {
            var proof=reverts.current(e);
            if(!proof.released() && proof.sourceHash().equals(failures.getFirst().get("source_hash")))
                return Map.of("eligible",true,"executionId",execution,"sourceHash",proof.sourceHash());
        } catch(IllegalStateException|IllegalArgumentException invalid) { /* Inconclusive evidence grants no action. */ }
        return Map.of("eligible",false,"reason","CURRENT_CONCLUSIVE_REVERT_REQUIRED");
    }
    private List<Map<String,Object>> rows(String sql,String id) {
        return jdbc.query(sql,(r,n)->{var row=new LinkedHashMap<String,Object>();var meta=r.getMetaData();
            for(int col=1;col<=meta.getColumnCount();col++) {Object value=r.getObject(col);
                if(value instanceof java.math.BigDecimal d)value=d.toPlainString();
                if(value instanceof Long l)value=l.toString();
                if(value instanceof java.sql.Timestamp t)value=t.toInstant().toString();
                row.put(meta.getColumnLabel(col),value);
            }return row;
        },id);
    }
}
