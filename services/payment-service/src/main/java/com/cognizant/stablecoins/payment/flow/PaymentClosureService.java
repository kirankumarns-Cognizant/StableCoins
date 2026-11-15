package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.flow.FlowContract.*;
import com.cognizant.stablecoins.payment.repository.PaymentRepository;
import com.cognizant.stablecoins.payment.model.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Payment owns state/history/outbox; other services own their business records. */
@Service @ConditionalOnProperty(name="app.flow.enabled",havingValue="true") @Transactional
public class PaymentClosureService {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Environment env;private final PaymentRepository payments;private final PaymentFlowService flow;
 private final FlowReadModels reads;private final LifecycleReadModels lifecycle;private final ClosureEvidence checker;
 @org.springframework.beans.factory.annotation.Autowired
 public PaymentClosureService(JdbcTemplate jdbc,ObjectMapper mapper,Environment env,PaymentRepository payments,PaymentFlowService flow){this(jdbc,mapper,env,payments,flow,new FlowReadModels(jdbc,mapper),new LifecycleReadModels(jdbc,mapper),new ClosureEvidence(jdbc,mapper,env));}
 PaymentClosureService(JdbcTemplate jdbc,ObjectMapper mapper,Environment env,PaymentRepository payments,PaymentFlowService flow,FlowReadModels reads,LifecycleReadModels lifecycle,ClosureEvidence checker){this.jdbc=jdbc;this.mapper=mapper;this.env=env;this.payments=payments;this.flow=flow;this.reads=reads;this.lifecycle=lifecycle;this.checker=checker;}
 public void advance(UUID id){
  var e=reads.execution(id);if(!e.status().equals("CONFIRMED"))return;
  var p=payments.lockByPaymentId(e.paymentId()).orElseThrow();
  String phase=jdbc.queryForList("select phase from payment_closure where execution_id=?",String.class,id).stream().findFirst().orElse(null);
  if(phase==null){
   if(!p.getStatus().equals("BLOCKCHAIN_CONFIRMED"))throw new IllegalStateException("CLOSURE_STATE_MISMATCH");
   jdbc.update("insert into payment_closure(execution_id,payment_id,phase) values (?,?,'SETTLEMENT_REQUESTED')",id,p.getPaymentId());
   flow.command(p,"SettlementRequested",new Command(id),"1.0");return;
  }
  if(Set.of("COMPLETED","REVIEW_REQUIRED").contains(phase))return;
  jdbc.update("update payment_closure set updated_at=now() where execution_id=?",id);
  var c=lifecycle.chain(e);var q=reads.quote(e.preparationId()).orElseThrow().document();boolean bank=q.payoutCurrency()!=null;
  if(phase.equals("SETTLEMENT_REQUESTED")){
   if(lifecycle.settlement(e,c).isEmpty()){set(id,phase,c.feeComplete()?"AWAITING_SETTLEMENT_LEDGER":"FEE_EVIDENCE_INCOMPLETE");return;}
   var capture=checker.capture(e,ControlLedgerEvidence.Stage.SETTLEMENT);if(!capture.findings().isEmpty())return;
   flow.transition(p,"SETTLEMENT_COMPLETED","USDC settlement and complete receipt fee journals acknowledged");flow.command(p,"SettlementCompleted",new Command(id),"1.0");set(id,"SETTLED",null);phase="SETTLED";
  }
  if(phase.equals("SETTLED")){
   if(bank){
    if(!env.getProperty("app.flow.execution-enabled",Boolean.class,false))return;
    flow.transition(p,"VISA_PAYOUT_PENDING","Explicitly simulated off-ramp and bank payout requested");flow.command(p,"VisaPayoutInitiated",new Command(id),"1.0");set(id,"PAYOUT_REQUESTED",null);return;
   }
   reconcile(p,id);return;
  }
  if(phase.equals("PAYOUT_REQUESTED")){
   var payout=lifecycle.payout(p.getPaymentId()).orElse(null);if(payout==null)return;lifecycle.requirePayout(e,q,c,payout);
   if(payout.status().equals("FAILED")){set(id,"REVIEW_REQUIRED","PAYOUT_FAILED_NO_REFUND_INFERRED");return;}
   if(payout.status().equals("UNKNOWN")){set(id,phase,"PAYOUT_UNKNOWN_PINNED");return;}
   if(!payout.status().equals("SUCCEEDED"))return;
   flow.command(p,"VisaPayoutCompleted",new Command(id),"1.0");set(id,"PAYOUT_COMPLETION_REQUESTED",null);return;
  }
  if(phase.equals("PAYOUT_COMPLETION_REQUESTED")){
   if(!checker.capture(e,ControlLedgerEvidence.Stage.COMPLETE).findings().isEmpty())return;
   flow.transition(p,"VISA_PAYOUT_COMPLETED","Simulated bank credit and payout completion journal acknowledged");reconcile(p,id);return;
  }
  if(phase.equals("RECONCILIATION_REQUESTED")){
   var reports=jdbc.queryForList("select reconciliation_id,status,source_hash from reconciliation where execution_id=? and payment_id=? and correlation_id=? order by created_at desc,reconciliation_id desc limit 1",id,p.getPaymentId(),p.getCorrelationId());
   if(reports.isEmpty())return;var report=reports.getFirst();
   if(!"MATCHED".equals(report.get("status"))){set(id,phase,"RECONCILIATION_"+report.get("status"));return;}
   var current=checker.capture(e,ControlLedgerEvidence.Stage.COMPLETE);
   if(!current.findings().isEmpty()||!checker.fingerprint(current).equals(report.get("source_hash"))){set(id,phase,"RECONCILIATION_EVIDENCE_CHANGED");return;}
   flow.transition(p,"RECONCILED","Owner reconciliation matched exact journals, receipt and optional simulated payout");
   flow.command(p,"ReconciliationCompleted",Map.of("executionId",id,"reconciliationId",report.get("reconciliation_id")),"1.0");set(id,"COMPLETED",null);
  }
 }
 private void reconcile(Payment p,UUID id){flow.transition(p,"RECONCILIATION_PENDING","Comparing application, ledger, blockchain and applicable simulated payout evidence");flow.command(p,"ReconciliationRequested",new Command(id),"1.0");set(id,"RECONCILIATION_REQUESTED",null);}
 private void set(UUID id,String phase,String failure){jdbc.update("update payment_closure set phase=?,failure_code=?,updated_at=now() where execution_id=?",phase,failure,id);}
}
