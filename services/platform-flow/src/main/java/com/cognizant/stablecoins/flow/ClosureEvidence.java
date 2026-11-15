package com.cognizant.stablecoins.flow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import java.util.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
import static com.cognizant.stablecoins.flow.LifecycleReadModels.*;
/** One read-only comparison shared by reconciliation and payment acknowledgement. */
public final class ClosureEvidence {
 public record Capture(Execution execution,Quote quote,Chain chain,Payout payout,UUID settlementId,List<Journal> journals,List<String> findings){}
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Environment env;
 public ClosureEvidence(JdbcTemplate jdbc,ObjectMapper mapper,Environment env){this.jdbc=jdbc;this.mapper=mapper;this.env=env;}
 public Capture capture(Execution e,ControlLedgerEvidence.Stage stage){
  var flows=new FlowReadModels(jdbc,mapper);var reads=new LifecycleReadModels(jdbc,mapper);
  var q=flows.quote(e.preparationId()).orElseThrow().document();var c=reads.chain(e);var p=reads.payout(e.paymentId()).orElse(null);var journals=reads.journals(e.paymentId());
  if(p!=null)reads.requirePayout(e,q,c,p);
  var findings=new ArrayList<>(ControlLedgerEvidence.inspect(e,q,c,p,journals,env,stage));
  if(p!=null&&p.status().equals("SUCCEEDED")){
   Boolean consumed=jdbc.queryForObject("select exists(select 1 from visa_liquidity_reservation where reservation_id=? and payment_id=? and correlation_id=? and provider_id=? and currency=? and amount=? and status='CONSUMED')",Boolean.class,p.reservationId(),e.paymentId(),e.correlationId(),p.providerId(),p.currency(),p.amount());
   if(!Boolean.TRUE.equals(consumed))findings.add("PAYOUT_LIQUIDITY_NOT_CONSUMED");
  }
  UUID settlement=reads.settlement(e,c).orElse(null);
  if(stage==ControlLedgerEvidence.Stage.COMPLETE&&settlement==null)findings.add("SETTLEMENT_EVIDENCE_MISSING");
  return new Capture(e,q,c,p,settlement,journals,List.copyOf(findings));
 }
 public String fingerprint(Capture c){return FlowJson.fingerprint(mapper,c);}
}
