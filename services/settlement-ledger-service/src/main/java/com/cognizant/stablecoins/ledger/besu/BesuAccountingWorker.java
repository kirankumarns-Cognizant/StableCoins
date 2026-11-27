package com.cognizant.stablecoins.ledger.besu;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/** Ledger-owned continuation of committed chain evidence. Never signs or updates payment state. */
@Component
@EnableScheduling
@ConditionalOnProperty(name={"app.besu.accounting.enabled","app.besu.accounting-workers.enabled"},havingValue="true")
public class BesuAccountingWorker {
    private final BesuAccountingWorkReads reads;
    private final BesuAccountingService accounting;
    private UUID cursor;
    @Autowired
    public BesuAccountingWorker(JdbcTemplate jdbc,BesuAccountingService accounting) {
        this(new BesuAccountingWorkReads(jdbc),accounting);
    }
    BesuAccountingWorker(BesuAccountingWorkReads reads,BesuAccountingService accounting) {
        this.reads=reads;this.accounting=accounting;
    }
    // Deliberately no enclosing transaction: each proxied owner call commits independently.
    @Scheduled(initialDelayString="${app.besu.accounting-workers.poll-ms:5000}",
               fixedDelayString="${app.besu.accounting-workers.poll-ms:5000}")
    public synchronized void poll() {
        try {
            var rows=reads.pending(cursor);
            for(var row:rows) {
                cursor=row.id();
                try {
                    var execution=row.execution();
                    accounting.submitted(execution);
                    if(row.terminal()) accounting.resolve(execution);
                } catch(RuntimeException failure) {deferred("posting",row.id());}
            }
            if(rows.size()<BesuAccountingWorkReads.BATCH_SIZE) cursor=null;
        } catch(RuntimeException failure) {deferred("scan",null);}
    }
    private static void deferred(String phase,UUID executionId) {
        // No candidate documents, signatures, exception messages or provider details.
        LoggerFactory.getLogger(BesuAccountingWorker.class).warn(
            "service=settlement-ledger-service event=besu_accounting_worker_deferred phase={} executionId={}",phase,executionId);
    }
}
