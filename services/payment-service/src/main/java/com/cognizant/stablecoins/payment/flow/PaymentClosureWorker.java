package com.cognizant.stablecoins.payment.flow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
@Component @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class PaymentClosureWorker {
 private final JdbcTemplate jdbc;private final PaymentClosureService flow;
 public PaymentClosureWorker(JdbcTemplate jdbc,PaymentClosureService flow){this.jdbc=jdbc;this.flow=flow;}
 @Scheduled(fixedDelayString="${app.flow.poll-ms:3000}") public void tick(){
  for(var id:jdbc.queryForList("select e.execution_id from payment_execution e left join payment_closure c on c.execution_id=e.execution_id where e.status='CONFIRMED' and (c.phase is null or c.phase not in ('COMPLETED','REVIEW_REQUIRED')) order by coalesce(c.updated_at,e.updated_at) limit 10",UUID.class))try{flow.advance(id);}catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(getClass()).warn("service=payment-service event=closure_deferred executionId={}",id);}
 }
}
