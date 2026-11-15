package com.cognizant.stablecoins.flow;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.math.BigDecimal;
import static com.cognizant.stablecoins.flow.FlowContract.*;
/** ADR-007 permits these parameterized reads. This class has no write method. */
public class FlowReadModels {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public FlowReadModels(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 public void requireCurrentInstruction(Request request){
  var rows=jdbc.query("select p.correlation_id,p.amount,p.source_currency,p.payment_rail,p.intent_snapshot::text,a.customer_id,b.customer_id beneficiary_customer_id,a.status account_status,b.status beneficiary_status,b.wallet_address from payment p join account a on a.account_id=p.sender_account_id join beneficiary b on b.beneficiary_id=p.beneficiary_id where p.payment_id=?",(rs,n)->{
   var intent=FlowJson.value(mapper,rs.getString("intent_snapshot"),JsonNode.class);
   return request.correlationId().equals(rs.getString("correlation_id"))&&request.sourceUsd().compareTo(rs.getBigDecimal("amount"))==0&&"USD".equals(rs.getString("source_currency"))&&request.requestedRail().equals(rs.getString("payment_rail"))&&request.customerId().equals(rs.getObject("customer_id",UUID.class))&&request.customerId().equals(rs.getObject("beneficiary_customer_id",UUID.class))&&"ACTIVE".equals(rs.getString("account_status"))&&"ACTIVE".equals(rs.getString("beneficiary_status"))&&request.recipientWallet().equalsIgnoreCase(rs.getString("wallet_address"))&&request.customerId().toString().equals(intent.path("customerId").asText())&&request.outcome().equals(intent.path("instruction").path("requiredOutcome").asText())&&request.deadline().equals(FlowJson.convert(mapper,intent.path("instruction").path("deliverBy"),java.time.Instant.class));
  },request.paymentId());
  if(rows.size()!=1||!rows.getFirst())throw new IllegalStateException("FLOW_INSTRUCTION_CHANGED");
 }
 public void requireRequested(Request request){
  var rows=jdbc.query("select request_document::text from payment_flow_preparation where request_id=? and payment_id=? and correlation_id=?",(rs,n)->FlowJson.value(mapper,rs.getString(1),Request.class),request.requestId(),request.paymentId(),request.correlationId());
  if(rows.size()!=1||!rows.getFirst().equals(request))throw new IllegalStateException("CANONICAL_PREPARATION_REQUIRED");
  requireCurrentInstruction(request);
 }
 public boolean validated(UUID executionId){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from compliance_execution_validation where execution_id=? and status='VALIDATED')",Boolean.class,executionId));}
 public void requireApproved(Execution execution){
  var rows=jdbc.query("select r.state,r.expires_at,r.document::text,p.intent_snapshot::text current_intent from routing_recommendation r join payment p on p.payment_id=r.payment_id where r.recommendation_id=? and r.payment_id=? and r.correlation_id=?",(rs,n)->{
   var document=FlowJson.value(mapper,rs.getString(3),JsonNode.class);
   return document.path("intent").equals(FlowJson.value(mapper,rs.getString("current_intent"),JsonNode.class))&&Set.of("APPROVED","EXECUTED").contains(rs.getString(1))&&rs.getTimestamp(2).toInstant().isAfter(java.time.Instant.now())&&execution.request().recipientWallet().equalsIgnoreCase(document.path("instruction").path("recipientWallet").asText())&&execution.preparationId().toString().equals(document.path("evidence").path("snapshotId").asText());
  },execution.recommendationId(),execution.paymentId(),execution.correlationId());
  if(rows.size()!=1||!rows.getFirst()||!execution.expiresAt().isAfter(java.time.Instant.now()))throw new IllegalStateException("CURRENT_APPROVAL_REQUIRED");
  requireRequested(execution.request());
 }
 public List<Stored<Quote>> quotes(UUID id){return jdbc.query("select status,document::text,failure_code from compliance_flow_quote where request_id=? order by provider_id",(rs,n)->new Stored<Quote>(rs.getString(1),FlowJson.value(mapper,rs.getString(2),Quote.class),rs.getString(3)),id);}
 public Optional<Stored<Quote>> quote(UUID id){
  return jdbc.query("select q.status,q.document::text,q.failure_code from compliance_flow_quote q join payment_flow_preparation p on p.request_id=q.request_id join routing_recommendation r on r.recommendation_id=p.recommendation_id and r.document #>> '{quote,quoteId}' = q.document ->> 'quoteId' where q.request_id=?",(rs,n)->new Stored<Quote>(rs.getString(1),FlowJson.value(mapper,rs.getString(2),Quote.class),rs.getString(3)),id).stream().findFirst();
 }
 public Optional<Stored<Treasury>> treasury(UUID id){return stored("blockchain_flow_observation",id,Treasury.class);}
 private <T>Optional<Stored<T>> stored(String table,UUID id,Class<T> type){
  return jdbc.query("select status,document::text,failure_code from "+table+" where request_id=?",(rs,n)->new Stored<T>(rs.getString(1),rs.getString(2)==null?null:FlowJson.value(mapper,rs.getString(2),type),rs.getString(3)),id).stream().findFirst();
 }
 public Execution execution(UUID id){
  return jdbc.query("select execution_id,recommendation_id,preparation_id,payment_id,correlation_id,status,request_document::text,transaction_id,expires_at from payment_execution where execution_id=?",(rs,n)->new Execution(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5),rs.getString(6),FlowJson.value(mapper,rs.getString(7),Request.class),rs.getObject(8,UUID.class),rs.getTimestamp(9).toInstant()),id).stream().findFirst().orElseThrow(()->new IllegalStateException("EXECUTION_CLAIM_REQUIRED"));
 }
 /** A fresh hold may shrink, but cannot expand the reviewed ETH reserve or change network/mode/wallet. */
 public void requireExecutionTransaction(Execution execution,UUID transactionId){
  var observed=treasury(execution.preparationId()).orElseThrow(()->new IllegalStateException("TREASURY_OBSERVATION_REQUIRED")).document();
  var request=execution.request();
  var rows=jdbc.query("select network,chain_id,token_contract,from_address,to_address,amount,gas_reserve_eth,evidence_mode from blockchain_transaction where transaction_id=? and payment_id=? and correlation_id=?",(rs,n)->
   observed.network().equals(rs.getString("network"))&&observed.chainId()==rs.getLong("chain_id")&&observed.tokenContract().equalsIgnoreCase(rs.getString("token_contract"))&&observed.walletAddress().equalsIgnoreCase(rs.getString("from_address"))&&request.recipientWallet().equalsIgnoreCase(rs.getString("to_address"))&&request.transferUsdc().compareTo(rs.getBigDecimal("amount"))==0&&rs.getBigDecimal("gas_reserve_eth")!=null&&rs.getBigDecimal("gas_reserve_eth").signum()>0&&rs.getBigDecimal("gas_reserve_eth").compareTo(observed.requiredGasEth())<=0&&observed.evidenceMode().equals(rs.getString("evidence_mode")),transactionId,execution.paymentId(),execution.correlationId());
  if(rows.size()!=1||!rows.getFirst())throw new IllegalStateException("TREASURY_EXECUTION_CHANGED");
 }
 public boolean providerReserved(Execution execution,Quote quote){
  if(quote.payoutCurrency()==null)return true;
  return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from visa_liquidity_reservation where payment_id=? and correlation_id=? and provider_id=? and currency=? and amount=? and status='PINNED')",Boolean.class,execution.paymentId(),execution.correlationId(),quote.providerId(),quote.payoutCurrency(),quote.payoutAmount()));
 }
 public java.math.BigDecimal providerAvailable(String provider,String currency){
  var values=jdbc.queryForList("select p.total-coalesce((select sum(r.amount) from visa_liquidity_reservation r where r.pocket_id=p.pocket_id and (r.status='PINNED' or (r.status='HELD' and r.expires_at>now()))),0) as available from visa_liquidity_pocket p where p.provider_id=? and p.currency=?",java.math.BigDecimal.class,provider,currency);
  return values.isEmpty()?java.math.BigDecimal.ZERO:values.getFirst().max(java.math.BigDecimal.ZERO);
 }
 public boolean funded(Execution execution,Quote quote){
  Request request=execution.request();
  var expected=Map.of("FUNDING","USD","CONVERSION_USD","USD","CONVERSION_USDC","USDC");
  for(var leg:expected.entrySet()){
   var rows=jdbc.query("select evidence::text from ledger_journal where payment_id=? and correlation_id=? and journal_type=? and status='POSTED'",(rs,n)->rs.getString(1),request.paymentId(),request.correlationId(),leg.getKey());
   if(rows.size()!=1)return false;
   var evidence=FlowJson.value(mapper,rows.getFirst(),JsonNode.class);var terms=evidence.path("terms");
   if(!execution.executionId().toString().equals(terms.path("conversionId").asText())||!quote.quoteId().toString().equals(terms.path("quoteId").asText())||quote.version()!=terms.path("quoteVersion").asInt()||!("SIMULATED-"+execution.executionId()).equals(terms.path("fundingReference").asText())||!"SIMULATED".equals(evidence.path("mode").asText())||terms.path("sourceUsd").decimalValue().compareTo(request.sourceUsd())!=0||terms.path("transferUsdc").decimalValue().compareTo(request.transferUsdc())!=0||!request.outcome().equals(terms.path("outcome").asText())||(quote.payoutAmount()!=null&&(terms.path("payoutAmount").isNull()||terms.path("payoutAmount").decimalValue().compareTo(quote.payoutAmount())!=0)))throw new IllegalStateException("ALLOCATION_EVIDENCE_MISMATCH");
  }
  return true;
 }
}
