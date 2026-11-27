package com.cognizant.stablecoins.payment.flow;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.repository.PaymentRepository;
import com.cognizant.stablecoins.payment.settlement.ExplicitSettlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Payment alone owns final status/history/outbox; no signer, ledger write or report write. */
@Service
@ConditionalOnProperty(name={"app.flow.enabled","app.besu.closure.enabled"},havingValue="true")
public class BesuPaymentClosureService {
    private static final List<String> STATES=List.of("FUNDED","BLOCKCHAIN_SUBMITTED","BLOCKCHAIN_CONFIRMED","SETTLEMENT_COMPLETED","RECONCILIATION_PENDING");
    private final PaymentRepository payments;private final PaymentFlowService flow;
    private final SettlementExecutionReads claims;private final BesuSettlementClosureReads reads;private final BesuPaymentClosureStore store;
    @org.springframework.beans.factory.annotation.Autowired
    public BesuPaymentClosureService(PaymentRepository payments,PaymentFlowService flow,JdbcTemplate jdbc,ObjectMapper mapper,Environment env) {
        this(payments,flow,new SettlementExecutionReads(jdbc),new BesuSettlementClosureReads(jdbc,mapper,env),new BesuPaymentClosureStore(jdbc));
    }
    BesuPaymentClosureService(PaymentRepository payments,PaymentFlowService flow,SettlementExecutionReads claims,
            BesuSettlementClosureReads reads,BesuPaymentClosureStore store) {
        this.payments=payments;this.flow=flow;this.claims=claims;this.reads=reads;this.store=store;
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public void advance(SettlementBinding.Execution e) {
        var i=e.instruction();var p=payments.lockByPaymentId(i.paymentId()).orElseThrow();
        claims.requireClaimed(e);
        if(!i.equals(ExplicitSettlementService.instruction(p)) || !i.correlationId().equals(p.getCorrelationId())
                || !"USD".equals(p.getSourceCurrency()) || !"STABLECOIN".equals(p.getPaymentRail()))throw conflict();
        var previous=store.find(e);
        if(previous.isPresent()) {if(!"RECONCILED".equals(p.getStatus()))throw conflict();return;}
        int state=STATES.indexOf(p.getStatus());if(state<0)throw conflict();
        var comparison=reads.current(e); // Fresh coherent owner sources, exact committed funding and allocation.
        if(!"MATCHED".equals(comparison.status()) || !comparison.findings().isEmpty())return;
        var report=reads.matchingReport(e,comparison); // Verify before any writes; absent is a normal wait.
        for(int next=state+1;next<STATES.size();next++) {
            String status=STATES.get(next);
            flow.transition(p,status,"Private DEMOUSD receipt and exact asset journals verified; fiat funding/conversion simulated");
            String event=switch(status) {
                case "BLOCKCHAIN_SUBMITTED"->"BesuBlockchainTransactionSubmitted";
                case "BLOCKCHAIN_CONFIRMED"->"BesuBlockchainTransactionConfirmed";
                case "SETTLEMENT_COMPLETED"->"BesuSettlementCompleted";
                default->"BesuReconciliationRequested";
            };
            flow.command(p,event,Map.of("executionId",e.executionId(),"executionFingerprint",SettlementBindingJson.fingerprint(e)),"1.0");
        }
        if(report.isEmpty())return;
        flow.transition(p,"RECONCILED","Payment owner matched fresh private receipt, committed funding and exact journals to the scoped reconciliation report");
        flow.command(p,"BesuReconciliationCompleted",Map.of("executionId",e.executionId(),"executionFingerprint",SettlementBindingJson.fingerprint(e),
            "reconciliationId",report.get().id(),"sourceHash",report.get().sourceHash()),"1.0");
        payments.flush(); // JDBC acknowledgement guard must see this transaction's JPA state/history/outbox.
        store.complete(e,report.get());
    }
    private static IllegalStateException conflict(){return new IllegalStateException("BESU_PAYMENT_CLOSURE_CONFLICT");}
}
