package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.payment.approval.ApprovalConflictException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.UUID;
@Component @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class PaymentFlowWorker {
 private final JdbcTemplate jdbc;private final PaymentFlowService flow;
 public PaymentFlowWorker(JdbcTemplate jdbc,PaymentFlowService flow){this.jdbc=jdbc;this.flow=flow;}
 @Scheduled(fixedDelayString="${app.flow.poll-ms:3000}")
 public void tick(){
  for(var id:jdbc.queryForList("select request_id from payment_flow_preparation where status='PENDING' order by created_at limit 10",UUID.class)){
   try{flow.finishPreparation(id);}catch(ApprovalConflictException error){flow.reviewPreparation(id,error.getErrorCode());}
   catch(RuntimeException error){deferred("preparation_deferred","requestId",id,error);}
  }
  for(var id:jdbc.queryForList("select execution_id from payment_execution where status not in ('CONFIRMED','REVIEW_REQUIRED') order by updated_at limit 10",UUID.class)){
   try{flow.advance(id);}catch(ApprovalConflictException error){flow.reviewExecution(id,error.getErrorCode());}
   catch(IllegalStateException error){
    if(java.util.Set.of("FLOW_INSTRUCTION_CHANGED","CURRENT_APPROVAL_REQUIRED","TREASURY_EXECUTION_CHANGED","ALLOCATION_EVIDENCE_MISMATCH").contains(error.getMessage()==null?"":error.getMessage()))flow.reviewExecution(id,error.getMessage());
    else deferred("execution_deferred","executionId",id,error);
   }
   catch(RuntimeException error){deferred("execution_deferred","executionId",id,error);}
  }
 }
 private void deferred(String event,String identifier,UUID id,RuntimeException error){
  Throwable cause=error;
  for(int depth=0;depth<10&&cause.getCause()!=null&&cause.getCause()!=cause;depth++)cause=cause.getCause();
  String sqlState=cause instanceof java.sql.SQLException sql&&sql.getSQLState()!=null&&sql.getSQLState().matches("[A-Z0-9]{5}")?sql.getSQLState():"NONE";
  // Never emit exception text, SQL parameters or stack traces: these can contain credentials/PII.
  LoggerFactory.getLogger(getClass()).warn("service=payment-service event={} {}={} errorType={} causeType={} sqlState={}",event,identifier,id,error.getClass().getSimpleName(),cause.getClass().getSimpleName(),sqlState);
 }
}
