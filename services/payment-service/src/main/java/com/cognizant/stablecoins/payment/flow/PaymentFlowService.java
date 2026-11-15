package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.cognizant.stablecoins.payment.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
/** Payment-owned coordinator. No model, RPC, private key or writes to another service's tables. */
@Service @ConditionalOnProperty(name="app.flow.enabled",havingValue="true") @Transactional
public class PaymentFlowService {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Environment env;private final FlowReadModels reads;
 private final PaymentRepository payments;private final AccountRepository accounts;private final BeneficiaryRepository beneficiaries;
 private final RecommendationService recommendations;private final OutboxEventRepository outbox;private final PaymentStatusHistoryRepository history;
 private final OwnerEvidenceAssembler assembler;
 @org.springframework.beans.factory.annotation.Autowired
 public PaymentFlowService(JdbcTemplate jdbc,ObjectMapper mapper,Environment env,PaymentRepository payments,AccountRepository accounts,BeneficiaryRepository beneficiaries,
   RecommendationService recommendations,OutboxEventRepository outbox,PaymentStatusHistoryRepository history,OwnerEvidenceAssembler assembler){this(jdbc,mapper,env,payments,accounts,beneficiaries,recommendations,outbox,history,assembler,new FlowReadModels(jdbc,mapper));}
 PaymentFlowService(JdbcTemplate jdbc,ObjectMapper mapper,Environment env,PaymentRepository payments,AccountRepository accounts,BeneficiaryRepository beneficiaries,
   RecommendationService recommendations,OutboxEventRepository outbox,PaymentStatusHistoryRepository history,OwnerEvidenceAssembler assembler,FlowReadModels reads){
  this.jdbc=jdbc;this.mapper=mapper;this.env=env;this.reads=reads;this.payments=payments;this.accounts=accounts;this.beneficiaries=beneficiaries;
  this.recommendations=recommendations;this.outbox=outbox;this.history=history;this.assembler=assembler;

 }
 public record Preparation(UUID requestId,String paymentId,String status,UUID recommendationId,String failureCode){}
 public record ExecutionView(UUID executionId,String paymentId,String status,UUID transactionId,String failureCode){}
 public Preparation prepare(String paymentId,UUID key,AuthorizedCustomer actor){
  var p=lock(paymentId,actor);
  var existing=jdbc.query("select request_id,payment_id,status,recommendation_id,failure_code from payment_flow_preparation where request_id=?",(rs,n)->new Preparation(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getString(5)),key);
  if(!existing.isEmpty()){if(!paymentId.equals(existing.getFirst().paymentId()))throw conflict("PREPARATION_KEY_CONFLICT");return existing.getFirst();}
  if(!"COMPLIANCE_APPROVED".equals(p.getStatus()))throw conflict("COMPLIANCE_APPROVAL_REQUIRED");
  if(!p.getIntent().instruction().deliverBy().isAfter(Instant.now()))throw conflict("INTENT_EXPIRED");
  Integer pending=jdbc.queryForObject("select count(*) from payment_flow_preparation where payment_id=? and status='PENDING'",Integer.class,paymentId);
  if(pending!=null&&pending>0)throw conflict("PREPARATION_ALREADY_PENDING");
  var beneficiary=beneficiaries.findById(p.getBeneficiaryId()).orElseThrow(()->conflict("BENEFICIARY_UNAVAILABLE"));
  var request=new Request(key,paymentId,p.getCorrelationId(),actor.customerId(),p.getAmount(),p.getAmount(),beneficiary.getWalletAddress(),p.getIntent().instruction().requiredOutcome().name(),p.getPaymentRail(),"USD-USDC-"+p.getIntent().instruction().requiredOutcome().name(),p.getIntent().instruction().deliverBy(),p.getIntent().policy().allowedProviderIds());
  jdbc.update("insert into payment_flow_preparation(request_id,payment_id,correlation_id,actor_id,request_document,status) values (?,?,?,?,cast(? as jsonb),'PENDING')",key,paymentId,p.getCorrelationId(),actor.actorId(),FlowJson.json(mapper,request));
  command(p,"RoutingEvidenceRequested",request,"1.0");return new Preparation(key,paymentId,"PENDING",null,null);
 }
 public Preparation preparation(String paymentId,UUID id,AuthorizedCustomer actor){
  lock(paymentId,actor);return jdbc.query("select request_id,payment_id,status,recommendation_id,failure_code from payment_flow_preparation where request_id=? and payment_id=?",(rs,n)->new Preparation(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getString(5)),id,paymentId).stream().findFirst().orElseThrow(()->conflict("PREPARATION_NOT_FOUND"));
 }
 public void finishPreparation(UUID id){
  var requests=jdbc.query("select request_document::text,actor_id from payment_flow_preparation where request_id=? and status='PENDING'",(rs,n)->Map.entry(FlowJson.value(mapper,rs.getString(1),Request.class),rs.getString(2)),id);
  if(requests.isEmpty())return;var request=requests.getFirst().getKey();var actor=new AuthorizedCustomer(request.customerId(),requests.getFirst().getValue());lock(request.paymentId(),actor);
  // Serialize competing workers using the payment lock and recheck after acquisition.
  if(!"PENDING".equals(jdbc.queryForObject("select status from payment_flow_preparation where request_id=?",String.class,id)))return;
  if(!request.deadline().isAfter(Instant.now()))throw conflict("PREPARATION_DEADLINE_EXPIRED");
  Boolean expired=jdbc.queryForObject("select created_at < now() - (? * interval '1 second') from payment_flow_preparation where request_id=?",Boolean.class,env.getProperty("app.flow.preparation-timeout-seconds",Integer.class,120),id);
  if(Boolean.TRUE.equals(expired))throw conflict("PREPARATION_TIMEOUT");
  var quotes=reads.quotes(id);var treasury=reads.treasury(id);if(quotes.isEmpty()||treasury.isEmpty())return;
  if(quotes.stream().anyMatch(q->!"READY".equals(q.status()))||!"READY".equals(treasury.get().status()))throw conflict("OWNER_EVIDENCE_REJECTED");
  reads.requireRequested(request);var prepared=assembler.assemble(request,quotes.stream().map(Stored::document).toList(),treasury.get().document());
  var view=recommendations.proposeCaptured(id,actor,prepared.context(),prepared.evidence(),prepared.quotes());
  jdbc.update("update payment_flow_preparation set status='READY',recommendation_id=? where request_id=?",view.recommendationId(),id);
 }
 public ExecutionView execute(String paymentId,UUID recommendationId,UUID key,int version,UUID quoteId,int quoteVersion,AuthorizedCustomer actor){
  var p=lock(paymentId,actor);
  var existing=jdbc.query("select execution_id,payment_id,status,transaction_id,failure_code,recommendation_id from payment_execution where payment_id=? or execution_id=?",(rs,n)->Map.entry(new ExecutionView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getString(5)),rs.getObject(6,UUID.class)),paymentId,key);
  if(!existing.isEmpty()){
   var row=existing.getFirst();if(existing.size()!=1||!row.getKey().executionId().equals(key)||!row.getKey().paymentId().equals(paymentId)||!row.getValue().equals(recommendationId))throw conflict("EXECUTION_KEY_CONFLICT");
   var view=recommendations.get(paymentId,recommendationId,actor);var quote=view.document().quote();
   if(view.version()!=version||quote.version()!=quoteVersion||!quote.quoteId().equals(quoteId))throw conflict("EXECUTION_MATERIAL_CONFLICT");return row.getKey();
  }
  enabled();var approved=recommendations.authorizeExecution(paymentId,recommendationId,version,quoteId,quoteVersion,actor);
  UUID preparationId=approved.document().evidence().snapshotId();
  var request=jdbc.query("select request_document::text from payment_flow_preparation where request_id=? and recommendation_id=? and status='READY'",(rs,n)->FlowJson.value(mapper,rs.getString(1),Request.class),preparationId,recommendationId).stream().findFirst().orElseThrow(()->conflict("TRUSTED_PREPARATION_REQUIRED"));
  reads.requireRequested(request);
  jdbc.update("insert into payment_execution(execution_id,payment_id,correlation_id,recommendation_id,preparation_id,actor_id,request_document,status,expires_at) values (?,?,?,?,?,?,cast(? as jsonb),'PREPARING',?)",key,paymentId,p.getCorrelationId(),recommendationId,preparationId,actor.actorId(),FlowJson.json(mapper,request),java.sql.Timestamp.from(approved.expiresAt()));
  command(p,"StablecoinExecutionRequested",new Command(key),"1.0");return new ExecutionView(key,paymentId,"PREPARING",null,null);
 }
 public ExecutionView execution(String paymentId,AuthorizedCustomer actor){lock(paymentId,actor);return jdbc.query("select execution_id,payment_id,status,transaction_id,failure_code from payment_execution where payment_id=?",(rs,n)->new ExecutionView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getString(5)),paymentId).stream().findFirst().orElseThrow(()->conflict("EXECUTION_NOT_FOUND"));}
 public void advance(UUID id){
  var execution=reads.execution(id);var request=execution.request();
  String actorId=jdbc.queryForObject("select actor_id from payment_execution where execution_id=?",String.class,id);var actor=new AuthorizedCustomer(request.customerId(),actorId);
  var p=lock(execution.paymentId(),actor);execution=reads.execution(id);
  if(Set.of("CONFIRMED","REVIEW_REQUIRED").contains(execution.status()))return;
  if(Set.of("PREPARING","AWAITING_LEDGER").contains(execution.status())&&!execution.expiresAt().isAfter(Instant.now()))throw conflict("EXECUTION_APPROVAL_EXPIRED");
  var transactions=jdbc.queryForList("select transaction_id,correlation_id,to_address,amount,status,tx_hash,reservation_status,reservation_expires_at from blockchain_transaction where payment_id=?",execution.paymentId());
  if(transactions.isEmpty())return;if(transactions.size()!=1)throw conflict("MULTIPLE_CHAIN_LEGS_REVIEW_REQUIRED");var tx=transactions.getFirst();
  if(!execution.correlationId().equals(tx.get("correlation_id"))||!request.recipientWallet().equalsIgnoreCase((String)tx.get("to_address"))||request.transferUsdc().compareTo((java.math.BigDecimal)tx.get("amount"))!=0)throw conflict("CHAIN_BINDING_MISMATCH");
  String chainStatus=(String)tx.get("status");UUID transactionId=(UUID)tx.get("transaction_id");
  if(Set.of("PREPARING","AWAITING_LEDGER","SUBMISSION_REQUESTED").contains(execution.status())&&"PREPARED".equals(chainStatus)&&!execution.expiresAt().isAfter(Instant.now()))throw conflict("EXECUTION_APPROVAL_EXPIRED");
  if("PREPARING".equals(execution.status())){
   if(!env.getProperty("app.flow.execution-enabled",Boolean.class,false))return;
   reads.requireApproved(execution);reads.requireExecutionTransaction(execution,transactionId);if(!reads.validated(id))return;
   var approvedQuote=reads.quote(execution.preparationId()).orElseThrow().document();
   if(approvedQuote.payoutCurrency()!=null&&!reads.providerReserved(execution,approvedQuote))return;
   if(!"PREPARED".equals(chainStatus)||!"HELD".equals(tx.get("reservation_status")))throw conflict("CHAIN_RESERVATION_REQUIRED");
   var expiry=jdbc.queryForObject("select reservation_expires_at from blockchain_transaction where transaction_id=?",java.time.OffsetDateTime.class,transactionId);
   if(expiry==null||!expiry.toInstant().isAfter(Instant.now()))throw conflict("CHAIN_RESERVATION_EXPIRED");
   var account=accounts.lockByAccountId(p.getSenderAccountId()).orElseThrow(()->conflict("ACCOUNT_UNAVAILABLE"));
   if(account.getBalance().compareTo(request.sourceUsd())<0)throw conflict("INSUFFICIENT_SIMULATED_USD");
   recommendations.markExecuted(p.getPaymentId(),execution.recommendationId(),actor);
   account.setBalance(account.getBalance().subtract(request.sourceUsd()));accounts.save(account);
   for(String state:List.of("QUOTE_ACCEPTED","RAIL_SELECTED","FUNDING_PENDING","FUNDED"))transition(p,state,"Approved wallet flow; fiat funding/conversion simulated");
   var quote=reads.quote(execution.preparationId()).orElseThrow().document();
   var terms=new LinkedHashMap<String,Object>();terms.put("conversionId",id);terms.put("quoteId",quote.quoteId());terms.put("quoteVersion",quote.version());terms.put("fundingReference","SIMULATED-"+id);terms.put("sourceUsd",request.sourceUsd());terms.put("transferUsdc",request.transferUsdc());terms.put("outcome",request.outcome());terms.put("payoutAmount",quote.payoutAmount());
   command(p,"StablecoinFundingCompleted",Map.of("simulation",true,"fundingMode","SIMULATED","sourceCurrency","USD","destinationAsset","USDC","terms",terms),"2.0");
   jdbc.update("update payment_execution set status='AWAITING_LEDGER',transaction_id=?,updated_at=now() where execution_id=?",transactionId,id);return;
  }
  if("AWAITING_LEDGER".equals(execution.status())){
   if(!env.getProperty("app.flow.execution-enabled",Boolean.class,false))return;
   reads.requireApproved(execution);var quote=reads.quote(execution.preparationId()).orElseThrow().document();
   if(!reads.funded(execution,quote))return;
   command(p,"BlockchainSubmissionRequested",new Command(id),"1.0");setExecution(id,"SUBMISSION_REQUESTED");return;
  }
  if(Set.of("FAILED","REVERTED","EXPIRED").contains(chainStatus))throw conflict("CHAIN_FAILURE_REVIEW_REQUIRED");
  if(Set.of("SUBMITTING","SUBMISSION_UNKNOWN").contains(chainStatus))return; // pinned; no reroute or refund
  if(Set.of("SUBMITTED","CONFIRMED").contains(chainStatus)){
   if("FUNDED".equals(p.getStatus()))transition(p,"BLOCKCHAIN_SUBMITTED","Persisted blockchain submission evidence");
   if(!"CONFIRMED".equals(chainStatus)){setExecution(id,"SUBMITTED");return;}
   Boolean journal=jdbc.queryForObject("select exists(select 1 from ledger_journal where payment_id=? and correlation_id=? and journal_type='BLOCKCHAIN_CONFIRMATION' and status='POSTED' and reference=?)",Boolean.class,p.getPaymentId(),p.getCorrelationId(),tx.get("tx_hash"));
   if(!Boolean.TRUE.equals(journal))return;
   if("BLOCKCHAIN_SUBMITTED".equals(p.getStatus()))transition(p,"BLOCKCHAIN_CONFIRMED","Receipt and ledger confirmation evidenced; reconciliation pending");
   setExecution(id,"CONFIRMED");
  }
 }
 public record ClosureView(UUID executionId,String paymentId,String phase,String failureCode){}
 public ClosureView closure(String paymentId,AuthorizedCustomer actor){
  lock(paymentId,actor);
  return jdbc.query("select execution_id,phase,failure_code from payment_closure where payment_id=?",(rs,n)->new ClosureView(rs.getObject(1,UUID.class),paymentId,rs.getString(2),rs.getString(3)),paymentId).stream().findFirst().orElse(new ClosureView(null,paymentId,"NOT_STARTED",null));
 }
 public void reviewPreparation(UUID id,String code){jdbc.update("update payment_flow_preparation set status='REVIEW_REQUIRED',failure_code=? where request_id=? and status='PENDING'",code,id);}
 public void reviewExecution(UUID id,String code){jdbc.update("update payment_execution set status='REVIEW_REQUIRED',failure_code=?,updated_at=now() where execution_id=? and status not in ('CONFIRMED','REVIEW_REQUIRED')",code,id);}
 private void setExecution(UUID id,String status){jdbc.update("update payment_execution set status=?,updated_at=now() where execution_id=?",status,id);}
 private Payment lock(String id,AuthorizedCustomer actor){var p=payments.lockByPaymentId(id).orElseThrow(()->new PaymentNotFoundException(id));if(p.getIntent()==null||actor==null||!p.getIntent().customerId().equals(actor.customerId()))throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH","Payment is outside customer scope");return p;}
 void transition(Payment p,String state,String reason){var h=new PaymentStatusHistory();h.setPaymentId(p.getPaymentId());h.setOldStatus(p.getStatus());h.setNewStatus(state);h.setReason(reason);h.setActorService("payment-service");history.save(h);p.setStatus(state);payments.save(p);}
 private void enabled(){if(!env.getProperty("app.flow.execution-enabled",Boolean.class,false))throw conflict("EXECUTION_DISABLED");}
 void command(Payment p,String type,Object payload,String version){var event=new DomainEvent<>(UUID.randomUUID().toString(),type,version,p.getPaymentId(),p.getCorrelationId(),payload);var row=new OutboxEvent();row.setEventId(event.getEventId());row.setEventType(type);row.setEventVersion(version);row.setAggregateId(p.getPaymentId());row.setCorrelationId(p.getCorrelationId());row.setPayload(FlowJson.json(mapper,event));row.setStatus("PENDING");outbox.save(row);}
 private static ApprovalConflictException conflict(String code){return new ApprovalConflictException(code);}
}
