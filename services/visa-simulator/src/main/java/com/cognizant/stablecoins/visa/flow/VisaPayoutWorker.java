package com.cognizant.stablecoins.visa.flow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
@Component @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class VisaPayoutWorker {
 private final JdbcTemplate jdbc;private final VisaPayoutFlow flow;
 public VisaPayoutWorker(JdbcTemplate jdbc,VisaPayoutFlow flow){this.jdbc=jdbc;this.flow=flow;}
 @Scheduled(fixedDelayString="${app.flow.poll-ms:3000}") public void tick(){
  for(var id:jdbc.queryForList("select execution_id from visa_transaction where status='PROCESSING' order by updated_at limit 10",UUID.class))try{flow.complete(id);}catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(getClass()).warn("service=visa-simulator event=payout_deferred executionId={}",id);}
 }
}
