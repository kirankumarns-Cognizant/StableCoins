package com.cognizant.stablecoins.reconciliation.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.flow.FlowContract.*;
import com.fasterxml.jackson.databind.*;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
@Service @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class ReconciliationService {
 public record Report(UUID reconciliationId,UUID executionId,String paymentId,String correlationId,String status,String sourceHash,JsonNode evidence,Instant observedAt){}
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Environment env;private final FlowReadModels flows;private final ClosureEvidence checker;
 @org.springframework.beans.factory.annotation.Autowired
 public ReconciliationService(JdbcTemplate jdbc,ObjectMapper mapper,Environment env){this(jdbc,mapper,env,new FlowReadModels(jdbc,mapper),new ClosureEvidence(jdbc,mapper,env));}
 ReconciliationService(JdbcTemplate jdbc,ObjectMapper mapper,Environment env,FlowReadModels flows,ClosureEvidence checker){this.jdbc=jdbc;this.mapper=mapper;this.env=env;this.flows=flows;this.checker=checker;}
 @SqsListener("${app.sqs.reconciliation-requested-queue-name}") @Transactional(isolation=Isolation.REPEATABLE_READ)
 public void consume(String body){
  var event=FlowJson.read(mapper,body,"ReconciliationRequested");var cmd=FlowJson.convert(mapper,event.payload(),Command.class);var e=new FlowReadModels(jdbc,mapper).execution(cmd.executionId());
  if(!e.paymentId().equals(event.paymentId())||!e.correlationId().equals(event.correlationId()))throw new IllegalStateException("FLOW_ENVELOPE_MISMATCH");
  run(UUID.fromString(event.eventId()),e.executionId());
 }
 @Transactional(isolation=Isolation.REPEATABLE_READ)
 public Report run(UUID reportId,UUID executionId){
  Objects.requireNonNull(reportId);var e=flows.execution(executionId);
  var prior=report(reportId);if(prior.isPresent()){
   if(!prior.get().executionId().equals(executionId))throw new IllegalStateException("RECONCILIATION_KEY_CONFLICT");return prior.get();
  }
  String state=jdbc.queryForObject("select status from payment where payment_id=? for share",String.class,e.paymentId());
  if(!state.equals("RECONCILIATION_PENDING")||!e.status().equals("CONFIRMED"))throw new IllegalStateException("RECONCILIATION_PREREQUISITES_REQUIRED");
  java.util.List<String> findings;JsonNode evidence;String hash;
  try {
   var capture=checker.capture(e,ControlLedgerEvidence.Stage.COMPLETE);findings=capture.findings();evidence=mapper.valueToTree(capture);hash=checker.fingerprint(capture);
  }catch(IllegalStateException error){
   if(!java.util.Set.of("CHAIN_BINDING_MISMATCH","PAYOUT_BINDING_MISMATCH","CHAIN_LEG_REQUIRED").contains(error.getMessage()==null?"":error.getMessage()))throw error;
   findings=java.util.List.of(error.getMessage());evidence=mapper.valueToTree(java.util.Map.of("executionId",executionId,"findings",findings,"evidenceAvailable",false));hash=FlowJson.fingerprint(mapper,evidence);
  }
  String status=findings.isEmpty()?"MATCHED":findings.stream().allMatch(x->x.startsWith("MISSING_")||x.endsWith("INCOMPLETE")||x.endsWith("MISSING"))?"PENDING":"MISMATCH";
  int inserted=jdbc.update("insert into reconciliation(reconciliation_id,execution_id,payment_id,correlation_id,status,source_hash,document) values (?,?,?,?,?,?,cast(? as jsonb)) on conflict(reconciliation_id) do nothing",reportId,executionId,e.paymentId(),e.correlationId(),status,hash,FlowJson.json(mapper,evidence));
  if(inserted==0)return report(reportId).orElseThrow();
  for(String finding:findings)jdbc.update("insert into reconciliation_detail(reconciliation_id,finding) values (?,?)",reportId,finding);
  return report(reportId).orElseThrow();
 }
 public Optional<Report> latest(String paymentId){return jdbc.query("select * from reconciliation where payment_id=? order by created_at desc,reconciliation_id desc limit 1",(rs,n)->view(rs),paymentId).stream().findFirst();}
 private Optional<Report> report(UUID id){return jdbc.query("select * from reconciliation where reconciliation_id=?",(rs,n)->view(rs),id).stream().findFirst();}
 private Report view(java.sql.ResultSet rs)throws java.sql.SQLException{return new Report(rs.getObject("reconciliation_id",UUID.class),rs.getObject("execution_id",UUID.class),rs.getString("payment_id"),rs.getString("correlation_id"),rs.getString("status"),rs.getString("source_hash"),FlowJson.value(mapper,rs.getString("document"),JsonNode.class),rs.getTimestamp("created_at").toInstant());}
}
