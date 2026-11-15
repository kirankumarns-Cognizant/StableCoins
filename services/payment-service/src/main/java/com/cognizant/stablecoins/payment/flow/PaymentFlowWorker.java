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
   catch(RuntimeException error){LoggerFactory.getLogger(getClass()).warn("service=payment-service event=preparation_deferred requestId={}",id);}
  }
  for(var id:jdbc.queryForList("select execution_id from payment_execution where status not in ('CONFIRMED','REVIEW_REQUIRED') order by updated_at limit 10",UUID.class)){
   try{flow.advance(id);}catch(ApprovalConflictException error){flow.reviewExecution(id,error.getErrorCode());}
   catch(IllegalStateException error){
    if(java.util.Set.of("FLOW_INSTRUCTION_CHANGED","CURRENT_APPROVAL_REQUIRED","TREASURY_EXECUTION_CHANGED","ALLOCATION_EVIDENCE_MISMATCH").contains(error.getMessage()==null?"":error.getMessage()))flow.reviewExecution(id,error.getMessage());
    else LoggerFactory.getLogger(getClass()).warn("service=payment-service event=execution_deferred executionId={}",id);
   }
   catch(RuntimeException error){LoggerFactory.getLogger(getClass()).warn("service=payment-service event=execution_deferred executionId={}",id);}
  }
 }
}
