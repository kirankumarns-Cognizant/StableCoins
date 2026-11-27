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
@ConditionalOnProperty(name={"app.flow.enabled","app.besu.closure.enabled","app.besu.closure-workers.enabled"},havingValue="true")
public class BesuPaymentClosureWorker {
    private final BesuClosureWorkReads reads;private final BesuPaymentClosureService owner;private UUID cursor;
    @org.springframework.beans.factory.annotation.Autowired
    public BesuPaymentClosureWorker(JdbcTemplate jdbc,BesuPaymentClosureService owner){this(new BesuClosureWorkReads(jdbc),owner);}
    BesuPaymentClosureWorker(BesuClosureWorkReads reads,BesuPaymentClosureService owner){this.reads=reads;this.owner=owner;}
    @Scheduled(initialDelayString="${app.besu.closure-workers.poll-ms:5000}",fixedDelayString="${app.besu.closure-workers.poll-ms:5000}")
    public synchronized void poll() {
        try {
            var rows=reads.pending(cursor,false);
            for(var row:rows) {
                cursor=row.id();
                try {owner.advance(row.execution());} catch(RuntimeException failure){deferred("execution",row.id());}
            }
            if(rows.size()<BesuClosureWorkReads.BATCH_SIZE)cursor=null;
        } catch(RuntimeException failure){deferred("scan",null);}
    }
    private static void deferred(String phase,UUID id){LoggerFactory.getLogger(BesuPaymentClosureWorker.class).warn(
        "service=payment-service event=besu_closure_worker_deferred phase={} executionId={}",phase,id);}
}
