package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Component
@EnableScheduling
@ConditionalOnProperty(name={"app.besu.accounting.enabled","app.besu.recovery.enabled","app.besu.recovery-workers.enabled"},havingValue="true")
public class BesuCompensationWorker {
    private final BesuRevertWorkReads reads;private final BesuCompensationService owner;private UUID cursor;
    @org.springframework.beans.factory.annotation.Autowired
    public BesuCompensationWorker(JdbcTemplate jdbc,BesuCompensationService owner){this(new BesuRevertWorkReads(jdbc),owner);}
    BesuCompensationWorker(BesuRevertWorkReads reads,BesuCompensationService owner){this.reads=reads;this.owner=owner;}
    @Scheduled(initialDelayString="${app.besu.recovery-workers.poll-ms:5000}",fixedDelayString="${app.besu.recovery-workers.poll-ms:5000}")
    public synchronized void poll() {
        try {
            var rows=reads.pending(cursor,true);
            for(var row:rows) {
                cursor=row.id();
                try {owner.compensate(row.execution());} catch(RuntimeException failure){deferred("execution",row.id());}
            }
            if(rows.size()<BesuRevertWorkReads.BATCH_SIZE)cursor=null;
        } catch(RuntimeException failure){deferred("scan",null);}
    }
    private static void deferred(String phase,UUID id){LoggerFactory.getLogger(BesuCompensationWorker.class).warn(
        "service=settlement-ledger-service event=besu_recovery_worker_deferred phase={} executionId={}",phase,id);}
}
