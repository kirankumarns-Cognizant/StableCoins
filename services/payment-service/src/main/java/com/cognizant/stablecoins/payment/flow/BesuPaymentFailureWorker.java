package com.cognizant.stablecoins.payment.flow;

import com.cognizant.stablecoins.flow.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Component
@EnableScheduling
@ConditionalOnProperty(name={"app.flow.enabled","app.besu.recovery.enabled","app.besu.recovery-workers.enabled"},havingValue="true")
public class BesuPaymentFailureWorker {
    private final BesuRevertWorkReads reads;private final BesuPaymentFailureService owner;private UUID cursor;
    @org.springframework.beans.factory.annotation.Autowired
    public BesuPaymentFailureWorker(JdbcTemplate jdbc,BesuPaymentFailureService owner){this(new BesuRevertWorkReads(jdbc),owner);}
    BesuPaymentFailureWorker(BesuRevertWorkReads reads,BesuPaymentFailureService owner){this.reads=reads;this.owner=owner;}
    @Scheduled(initialDelayString="${app.besu.recovery-workers.poll-ms:5000}",fixedDelayString="${app.besu.recovery-workers.poll-ms:5000}")
    public synchronized void poll() {
        try {
            var rows=reads.pending(cursor,false);
            for(var row:rows) {
                cursor=row.id();
                try {owner.advance(row.execution());} catch(RuntimeException failure){deferred("execution",row.id());}
            }
            if(rows.size()<BesuRevertWorkReads.BATCH_SIZE)cursor=null;
        } catch(RuntimeException failure){deferred("scan",null);}
    }
    private static void deferred(String phase,UUID id){LoggerFactory.getLogger(BesuPaymentFailureWorker.class).warn(
        "service=payment-service event=besu_recovery_worker_deferred phase={} executionId={}",phase,id);}
}
