package com.cognizant.stablecoins.reconciliation.besu;

import com.cognizant.stablecoins.flow.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Component
@EnableScheduling
@ConditionalOnProperty(name={"app.besu.reconciliation.enabled","app.besu.closure-workers.enabled"},havingValue="true")
public class BesuReconciliationWorker {
    private final BesuClosureWorkReads reads;private final BesuReconciliationService owner;private UUID cursor;
    @org.springframework.beans.factory.annotation.Autowired
    public BesuReconciliationWorker(JdbcTemplate jdbc,BesuReconciliationService owner){this(new BesuClosureWorkReads(jdbc),owner);}
    BesuReconciliationWorker(BesuClosureWorkReads reads,BesuReconciliationService owner){this.reads=reads;this.owner=owner;}
    @Scheduled(initialDelayString="${app.besu.closure-workers.poll-ms:5000}",fixedDelayString="${app.besu.closure-workers.poll-ms:5000}")
    public synchronized void poll() {
        try {
            var rows=reads.pending(cursor,true);
            for(var row:rows) {
                cursor=row.id();
                try {owner.runCurrent(row.execution());} catch(RuntimeException failure){deferred("execution",row.id());}
            }
            if(rows.size()<BesuClosureWorkReads.BATCH_SIZE)cursor=null;
        } catch(RuntimeException failure){deferred("scan",null);}
    }
    private static void deferred(String phase,UUID id){LoggerFactory.getLogger(BesuReconciliationWorker.class).warn(
        "service=reconciliation-service event=besu_closure_worker_deferred phase={} executionId={}",phase,id);}
}
