package com.cognizant.stablecoins.visa.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.flow.FlowContract.*;
import com.cognizant.stablecoins.visa.liquidity.VisaPaymentAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class VisaPayoutFlow {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Environment env;private final VisaPaymentAdapter adapter;
 public VisaPayoutFlow(JdbcTemplate jdbc,ObjectMapper mapper,Environment env,VisaPaymentAdapter adapter){this.jdbc=jdbc;this.mapper=mapper;this.env=env;this.adapter=adapter;}
 @Transactional @SqsListener("${app.sqs.visa-payout-queue-name}")
 public void initiate(String body){
  String type=FlowJson.value(mapper,body,com.fasterxml.jackson.databind.JsonNode.class).path("eventType").asText();
  if(!Set.of("StablecoinExecutionRequested","VisaPayoutInitiated").contains(type))throw new IllegalArgumentException("INVALID_PAYOUT_EVENT");
  var event=FlowJson.read(mapper,body,type);var command=FlowJson.convert(mapper,event.payload(),Command.class);var e=new FlowReadModels(jdbc,mapper).execution(command.executionId());
  if(!event.paymentId().equals(e.paymentId())||!event.correlationId().equals(e.correlationId()))throw new IllegalStateException("FLOW_ENVELOPE_MISMATCH");
  var flows=new FlowReadModels(jdbc,mapper);var reads=new LifecycleReadModels(jdbc,mapper);
  var q=flows.quote(e.preparationId()).orElseThrow().document();
  if(type.equals("StablecoinExecutionRequested")){
   if(q.payoutCurrency()==null)return;
   if(flows.providerReserved(e,q))return;
   if(!env.getProperty("app.flow.execution-enabled",Boolean.class,false))throw new IllegalStateException("EXECUTION_DISABLED");
   flows.requireApproved(e);if(!flows.validated(e.executionId()))throw new IllegalStateException("QUOTE_VALIDATION_REQUIRED");
   var reserved=adapter.reserve(e.paymentId(),e.correlationId(),q.providerId(),VisaPaymentAdapter.Currency.valueOf(q.payoutCurrency()),q.payoutAmount());
   if(!adapter.pin(reserved.reservationId()).status().equals("PINNED"))throw new IllegalStateException("PROVIDER_RESERVATION_REQUIRED");return;
  }
  if(reads.payout(e.paymentId()).isPresent()){reads.requirePayout(e,q,reads.chain(e),reads.payout(e.paymentId()).orElseThrow());return;}
  if(!env.getProperty("app.flow.execution-enabled",Boolean.class,false))throw new IllegalStateException("EXECUTION_DISABLED");
  var c=reads.chain(e);if(reads.settlement(e,c).isEmpty()||!e.status().equals("CONFIRMED"))throw new IllegalStateException("SETTLEMENT_REQUIRED");
  String state=jdbc.queryForObject("select status from payment where payment_id=?",String.class,e.paymentId());
  if(!"VISA_PAYOUT_PENDING".equals(state))throw new IllegalStateException("PAYOUT_INSTRUCTION_REQUIRED");
  if(!flows.providerReserved(e,q))throw new IllegalStateException("PROVIDER_RESERVATION_REQUIRED");
  if(q.payoutCurrency()==null)throw new IllegalStateException("WALLET_OUTCOME_HAS_NO_FIAT_PAYOUT");
  adapter.createPayout(new VisaPaymentAdapter.PayoutRequest(e.executionId(),e.paymentId(),e.correlationId(),q.providerId(),VisaPaymentAdapter.Currency.valueOf(q.payoutCurrency()),q.payoutAmount(),q.quoteId(),q.version(),c.hash(),env.getRequiredProperty("visa.simulator.payout-scenario")));
 }
 @Transactional public void complete(UUID executionId){
  var e=new FlowReadModels(jdbc,mapper).execution(executionId);var evidence=new ClosureEvidence(jdbc,mapper,env).capture(e,ControlLedgerEvidence.Stage.PAYOUT_INITIATED);
  if(!evidence.findings().isEmpty())return; // Wait for immutable off-ramp/payable acknowledgement.
  var p=evidence.payout();if(p!=null&&p.status().equals("PROCESSING"))adapter.finishPayout(p.payoutId());
 }
 @Transactional public LifecycleReadModels.Payout resolve(UUID payoutId,UUID operationId,boolean succeeded){
  var ids=jdbc.queryForList("select execution_id from visa_transaction where payout_id=?",UUID.class,payoutId);if(ids.size()!=1)throw new IllegalArgumentException("PAYOUT_NOT_FOUND");
  var e=new FlowReadModels(jdbc,mapper).execution(ids.getFirst());var reads=new LifecycleReadModels(jdbc,mapper);var p=reads.payout(e.paymentId()).orElseThrow();var q=new FlowReadModels(jdbc,mapper).quote(e.preparationId()).orElseThrow().document();var c=reads.chain(e);reads.requirePayout(e,q,c,p);
  if(succeeded){
   var journals=reads.journals(e.paymentId());
   if(journals.stream().noneMatch(j->j.type().equals("PAYOUT_INITIATION_"+p.currency())&&j.status().equals("POSTED")))throw new IllegalStateException("PAYOUT_ACCOUNTING_REQUIRED");
  }
  return adapter.resolvePayout(payoutId,operationId,succeeded);
 }
}
