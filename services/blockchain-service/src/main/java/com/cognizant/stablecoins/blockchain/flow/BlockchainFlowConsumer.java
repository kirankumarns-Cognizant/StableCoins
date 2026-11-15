package com.cognizant.stablecoins.blockchain.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.blockchain.service.BlockchainTransactionService;
import com.cognizant.stablecoins.blockchain.repository.BlockchainTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
/** Invokes the existing reservation/nonce/signing/recovery implementation, never a second transfer path. */
@Component @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class BlockchainFlowConsumer {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Environment env;
 private final BlockchainTransactionService chain;private final BlockchainTransactionRepository transactions;private final FlowReadModels reads;
 @org.springframework.beans.factory.annotation.Autowired
 public BlockchainFlowConsumer(JdbcTemplate jdbc,ObjectMapper mapper,Environment env,BlockchainTransactionService chain,BlockchainTransactionRepository transactions){this(jdbc,mapper,env,chain,transactions,new FlowReadModels(jdbc,mapper));}
 BlockchainFlowConsumer(JdbcTemplate jdbc,ObjectMapper mapper,Environment env,BlockchainTransactionService chain,BlockchainTransactionRepository transactions,FlowReadModels reads){
  this.jdbc=jdbc;this.mapper=mapper;this.env=env;this.chain=chain;this.transactions=transactions;this.reads=reads;

 }
 @SqsListener("${app.sqs.flow-treasury-queue-name}")
 public void observe(String body){
  var event=FlowJson.read(mapper,body,"RoutingEvidenceRequested");var request=FlowJson.convert(mapper,event.payload(),Request.class);bind(event,request);reads.requireRequested(request);
  String fingerprint=FlowJson.fingerprint(mapper,request);
  var existing=jdbc.queryForList("select request_fingerprint from blockchain_flow_observation where request_id=?",String.class,request.requestId());
  if(!existing.isEmpty()){if(!fingerprint.equals(existing.getFirst()))throw new IllegalStateException("TREASURY_REQUEST_CONFLICT");return;}
  var readiness=chain.getReadiness(request.recipientWallet(),request.transferUsdc());
  jdbc.update("insert into blockchain_flow_observation(request_id,request_fingerprint,status,document) values (?,?,'READY',cast(? as jsonb)) on conflict(request_id) do nothing",request.requestId(),fingerprint,FlowJson.json(mapper,readiness));
 }
 @SqsListener("${app.sqs.flow-reservation-queue-name}")
 public void reserve(String body){
  enabled();var execution=command(body,"StablecoinExecutionRequested");
  var existing=transactions.findByPaymentId(execution.paymentId());
  if(existing.isPresent()&&!"PREPARED".equals(existing.get().getStatus()))return;
  reads.requireApproved(execution);
  if(!reads.validated(execution.executionId()))throw new IllegalStateException("COMPLIANCE_VALIDATION_PENDING");
  chain.prepareTransfer(execution.paymentId(),execution.correlationId(),execution.request().recipientWallet(),execution.request().transferUsdc());
 }
 @SqsListener("${app.sqs.flow-submission-queue-name}")
 public void submit(String body){
  enabled();var execution=command(body,"BlockchainSubmissionRequested");
  var tx=transactions.findById(Objects.requireNonNull(execution.transactionId(),"TRANSACTION_BINDING_REQUIRED")).orElseThrow();
  if(!execution.paymentId().equals(tx.getPaymentId())||!execution.correlationId().equals(tx.getCorrelationId())||!execution.request().recipientWallet().equalsIgnoreCase(tx.getToAddress())||execution.request().transferUsdc().compareTo(tx.getAmount())!=0)throw new IllegalStateException("TRANSACTION_BINDING_MISMATCH");
  // A redelivery after signing is observation only, including SUBMISSION_UNKNOWN.
  if(!"PREPARED".equals(tx.getStatus()))return;
  if(!"SUBMISSION_REQUESTED".equals(execution.status()))throw new IllegalStateException("SUBMISSION_NOT_AUTHORIZED");
  reads.requireApproved(execution);reads.requireExecutionTransaction(execution,tx.getTransactionId());
  if(!reads.validated(execution.executionId()))throw new IllegalStateException("COMPLIANCE_VALIDATION_PENDING");
  var quote=reads.quote(execution.preparationId()).orElseThrow().document();
  if(!reads.funded(execution,quote))throw new IllegalStateException("LEDGER_ALLOCATION_PENDING");
  chain.submitTransaction(tx.getTransactionId());
 }
 /** Recovery remains active when new execution is disabled. Never resubmits signed bytes. */
 @Scheduled(fixedDelayString="${app.flow.poll-ms:3000}")
 public void receipts(){
  for(String status:List.of("SUBMITTING","SUBMITTED","SUBMISSION_UNKNOWN")){
   for(var tx:transactions.findByStatus(status).stream().limit(5).toList()){
    try{chain.confirmTransaction(tx.getTransactionId());}
    catch(RuntimeException error){LoggerFactory.getLogger(getClass()).warn("service=blockchain-service event=receipt_poll_deferred paymentId={} correlationId={} txHash={}",tx.getPaymentId(),tx.getCorrelationId(),tx.getTxHash());}
   }
  }
 }
 private Execution command(String body,String type){var event=FlowJson.read(mapper,body,type);var execution=reads.execution(FlowJson.convert(mapper,event.payload(),Command.class).executionId());bind(event,execution.request());return execution;}
 private void enabled(){if(!env.getProperty("app.flow.execution-enabled",Boolean.class,false))throw new IllegalStateException("EXECUTION_DISABLED");}
 private static void bind(FlowJson.Envelope event,Request request){if(!event.paymentId().equals(request.paymentId())||!event.correlationId().equals(request.correlationId()))throw new IllegalArgumentException("FLOW_ENVELOPE_MISMATCH");}
}
