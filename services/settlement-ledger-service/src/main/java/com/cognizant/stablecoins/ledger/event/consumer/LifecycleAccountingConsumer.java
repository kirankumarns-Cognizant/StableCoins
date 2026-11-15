package com.cognizant.stablecoins.ledger.event.consumer;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.flow.FlowContract.*;
import com.cognizant.stablecoins.flow.LifecycleReadModels.*;
import com.cognizant.stablecoins.ledger.accounting.*;
import com.fasterxml.jackson.databind.*;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Ledger-owned commands reuse the authoritative posting service and accepted templates. */
@Service @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class LifecycleAccountingConsumer {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Environment env;private final PaymentAccountingService accounting;
 public LifecycleAccountingConsumer(JdbcTemplate jdbc,ObjectMapper mapper,Environment env,PaymentAccountingService accounting){this.jdbc=jdbc;this.mapper=mapper;this.env=env;this.accounting=accounting;}
 @Transactional @SqsListener("${app.sqs.receipt-observed-queue-name}")
 public void receipt(String body){
  var event=FlowJson.read(mapper,body,"BlockchainReceiptObserved");UUID tx=UUID.fromString(event.payload().path("transactionId").asText());
  var ids=jdbc.queryForList("select execution_id from payment_execution where transaction_id=? and payment_id=? and correlation_id=?",UUID.class,tx,event.paymentId(),event.correlationId());
  if(ids.size()!=1)throw new IllegalStateException("EXECUTION_BINDING_REQUIRED");
  var e=new FlowReadModels(jdbc,mapper).execution(ids.getFirst());var c=new LifecycleReadModels(jdbc,mapper).chain(e);
  if(!Set.of("CONFIRMED","REVERTED").contains(c.status()))throw new IllegalStateException("MINED_RECEIPT_REQUIRED");
  fee(accounting.context(e.paymentId(),e.correlationId()),c); // Reversion consumes gas but never posts successful USDC confirmation.
 }
 @Transactional @SqsListener("${app.sqs.settlement-requested-queue-name}")
 public void settle(String body){
  var event=FlowJson.read(mapper,body,"SettlementRequested");var e=execution(event);
  if(!e.status().equals("CONFIRMED"))throw new IllegalStateException("CONFIRMED_EXECUTION_REQUIRED");
  var reads=new LifecycleReadModels(jdbc,mapper);var c=reads.chain(e);
  if(!"CONFIRMED".equals(c.status()))throw new IllegalStateException("CHAIN_NOT_CONFIRMED");
  var context=accounting.context(e.paymentId(),e.correlationId());fee(context,c);
  var existing=reads.settlement(e,c);if(existing.isPresent())return;
  var q=new FlowReadModels(jdbc,mapper).quote(e.preparationId()).orElseThrow().document();
  var journals=reads.journals(e.paymentId());var findings=ControlLedgerEvidence.inspect(e,q,c,null,journals,env,ControlLedgerEvidence.Stage.SETTLEMENT);
  if(!findings.isEmpty())throw new IllegalStateException("SETTLEMENT_ACCOUNTING_INCOMPLETE");
  UUID confirmation=journals.stream().filter(j->j.type().equals("BLOCKCHAIN_CONFIRMATION")).findFirst().orElseThrow().journalId();
  UUID fee=journals.stream().filter(j->j.type().equals("NETWORK_FEE")).findFirst().orElseThrow().journalId();
  jdbc.update("insert into settlement(settlement_id,payment_id,journal_id,settlement_type,asset,amount,status,settled_at,correlation_id,transaction_hash,fee_journal_id) values (?,?,?,'BLOCKCHAIN','USDC',?,'COMPLETED',now(),?,?,?)",UUID.randomUUID(),e.paymentId(),confirmation,c.amount(),e.correlationId(),c.hash(),fee);
 }
 @Transactional @SqsListener("${app.sqs.ledger-payout-queue-name}")
 public void payout(String body){
  String type=FlowJson.value(mapper,body,JsonNode.class).path("eventType").asText();
  if(!Set.of("VisaPayoutInitiated","VisaPayoutCompleted").contains(type))throw new IllegalArgumentException("INVALID_PAYOUT_EVENT");
  var event=FlowJson.read(mapper,body,type);var e=execution(event);var flows=new FlowReadModels(jdbc,mapper);var reads=new LifecycleReadModels(jdbc,mapper);
  var c=reads.chain(e);if(reads.settlement(e,c).isEmpty())throw new IllegalStateException("SETTLEMENT_REQUIRED");
  var q=flows.quote(e.preparationId()).orElseThrow().document();var p=reads.payout(e.paymentId()).orElseThrow(()->new IllegalStateException("PAYOUT_ACCEPTANCE_REQUIRED"));reads.requirePayout(e,q,c,p);
  var context=accounting.context(e.paymentId(),e.correlationId());
  if(type.equals("VisaPayoutInitiated")){
   if(!Set.of("PROCESSING","UNKNOWN","SUCCEEDED","FAILED").contains(p.status())||p.reservationId()==null)throw new IllegalStateException("PAYOUT_NOT_ACCEPTED");
   accounting.initiatePayout(context,c.hash(),mode(c),p.reference());
  }else{
   if(!p.status().equals("SUCCEEDED"))throw new IllegalStateException("PAYOUT_NOT_SUCCESSFUL");
   accounting.completePayout(context,c.hash(),p.reference(),PaymentAccountingService.PayoutStatus.SUCCEEDED);
  }
 }
 private Execution execution(FlowJson.Envelope event){var cmd=FlowJson.convert(mapper,event.payload(),Command.class);var e=new FlowReadModels(jdbc,mapper).execution(cmd.executionId());if(!e.paymentId().equals(event.paymentId())||!e.correlationId().equals(event.correlationId()))throw new IllegalStateException("FLOW_ENVELOPE_MISMATCH");return e;}
 private void fee(PaymentAccountingService.Context context,Chain c){
  if(!c.feeComplete()||c.blockNumber()==null||c.gasUsed()==null||c.effectiveGasPrice()==null||c.additionalFeeWei()==null||c.totalFeeWei()==null)throw new IllegalStateException("FEE_EVIDENCE_INCOMPLETE");
  accounting.networkFee(context,new PaymentAccountingService.ReceiptFee(c.hash(),c.chainId(),c.blockNumber(),c.gasUsed().toBigIntegerExact(),c.effectiveGasPrice().toBigIntegerExact(),c.additionalFeeWei().toBigIntegerExact(),c.totalFeeWei().toBigIntegerExact(),true,PaymentAccountingService.FeeSource.TRANSACTION_RECEIPT),mode(c));
 }
 private PostingEvidence.Mode mode(Chain c){return c.mode().equals("LIVE")?PostingEvidence.Mode.REAL_TESTNET:PostingEvidence.Mode.MOCK;}
}
