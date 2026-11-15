package com.cognizant.stablecoins.flow;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
/** ADR-007 read-only evidence. No owner-table updates or financial commands. */
public final class LifecycleReadModels {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final FlowReadModels flows;
 public LifecycleReadModels(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;this.flows=new FlowReadModels(jdbc,mapper);}
 public record Chain(UUID transactionId,String paymentId,String correlationId,String network,long chainId,String tokenContract,String sender,String recipient,BigDecimal amount,String hash,String status,Long blockNumber,BigDecimal gasUsed,BigDecimal effectiveGasPrice,BigDecimal additionalFeeWei,BigDecimal totalFeeWei,boolean feeComplete,String feeReason,String mode,int confirmations){}
 public record Payout(UUID payoutId,UUID executionId,String paymentId,String correlationId,String providerId,String currency,BigDecimal amount,String status,String reference,UUID reservationId,UUID quoteId,int quoteVersion,String hash,String mode,String scenario){}
 public record Entry(String accountCode,String asset,BigDecimal debit,BigDecimal credit){}
 public record Journal(UUID journalId,String paymentId,String correlationId,String type,String status,String reference,JsonNode evidence,List<Entry> entries){}
 public Chain chain(Execution e){
  var list=jdbc.query("select * from blockchain_transaction where payment_id=?",(r,n)->new Chain(r.getObject("transaction_id",UUID.class),r.getString("payment_id"),r.getString("correlation_id"),r.getString("network"),r.getLong("chain_id"),r.getString("token_contract"),r.getString("from_address"),r.getString("to_address"),r.getBigDecimal("amount"),r.getString("tx_hash"),r.getString("status"),r.getObject("block_number",Long.class),r.getBigDecimal("gas_used"),r.getBigDecimal("effective_gas_price"),r.getBigDecimal("additional_fee_wei"),r.getBigDecimal("total_fee_wei"),r.getBoolean("fee_components_complete"),r.getString("fee_evidence_reason"),r.getString("evidence_mode"),r.getInt("confirmation_count")),e.paymentId());
  if(list.size()!=1)throw new IllegalStateException("CHAIN_LEG_REQUIRED");var c=list.getFirst();
  var t=flows.treasury(e.preparationId()).orElseThrow().document();
  if(!Objects.equals(c.transactionId(),e.transactionId())||!c.correlationId().equals(e.correlationId())||!c.recipient().equalsIgnoreCase(e.request().recipientWallet())||c.amount().compareTo(e.request().transferUsdc())!=0||!c.sender().equalsIgnoreCase(t.walletAddress())||!c.tokenContract().equalsIgnoreCase(t.tokenContract())||c.chainId()!=t.chainId()||!c.network().equals(t.network())||!c.mode().equals(t.evidenceMode()))throw new IllegalStateException("CHAIN_BINDING_MISMATCH");return c;
 }
 public Optional<Payout> payout(String paymentId){return jdbc.query("select * from visa_transaction where payment_id=?",(r,n)->new Payout(r.getObject("payout_id",UUID.class),r.getObject("execution_id",UUID.class),r.getString("payment_id"),r.getString("correlation_id"),r.getString("provider_id"),r.getString("currency"),r.getBigDecimal("amount"),r.getString("status"),r.getString("reference"),r.getObject("reservation_id",UUID.class),r.getObject("quote_id",UUID.class),r.getInt("quote_version"),r.getString("transaction_hash"),r.getString("evidence_mode"),r.getString("scenario")),paymentId).stream().findFirst();}
 public List<Journal> journals(String paymentId){return jdbc.query("select * from ledger_journal where payment_id=? order by created_at,journal_id",(r,n)->new Journal(r.getObject("journal_id",UUID.class),r.getString("payment_id"),r.getString("correlation_id"),r.getString("journal_type"),r.getString("status"),r.getString("reference"),r.getString("evidence")==null?mapper.nullNode():FlowJson.value(mapper,r.getString("evidence"),JsonNode.class),entries(r.getObject("journal_id",UUID.class))),paymentId);}
 private List<Entry> entries(UUID id){return jdbc.query("select a.account_code,e.asset,e.debit,e.credit from ledger_entry e join ledger_account a on a.ledger_account_id=e.ledger_account_id where e.journal_id=? order by a.account_code",(r,n)->new Entry(r.getString(1),r.getString(2),r.getBigDecimal(3),r.getBigDecimal(4)),id);}
 public Optional<UUID> settlement(Execution e,Chain c){return jdbc.query("select settlement_id from settlement where payment_id=? and correlation_id=? and settlement_type='BLOCKCHAIN' and status='COMPLETED' and transaction_hash=? and amount=? and fee_journal_id is not null",(r,n)->r.getObject(1,UUID.class),e.paymentId(),e.correlationId(),c.hash(),c.amount()).stream().findFirst();}
 public void requirePayout(Execution e,Quote q,Chain c,Payout p){
  if(q.payoutAmount()==null||!p.executionId().equals(e.executionId())||!p.paymentId().equals(e.paymentId())||!p.correlationId().equals(e.correlationId())||!p.providerId().equals(q.providerId())||!p.currency().equals(q.payoutCurrency())||p.amount().compareTo(q.payoutAmount())!=0||!p.quoteId().equals(q.quoteId())||p.quoteVersion()!=q.version()||!p.hash().equalsIgnoreCase(c.hash())||!p.mode().equals("SIMULATED"))throw new IllegalStateException("PAYOUT_BINDING_MISMATCH");
 }
}
