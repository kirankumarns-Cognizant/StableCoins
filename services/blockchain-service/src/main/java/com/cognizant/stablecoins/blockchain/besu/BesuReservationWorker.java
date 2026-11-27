package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.SettlementClaimWorkReads;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/** Acquires capacity for an existing owner claim; never creates claims, signs or debits. */
@Component
@ConditionalOnProperty(name={"app.besu.enabled","app.besu.funding-workers.enabled"},havingValue="true")
public class BesuReservationWorker {
    private final SettlementClaimWorkReads reads;
    private final BesuExecutionReservationService reservations;
    private UUID cursor;
    @Autowired
    public BesuReservationWorker(JdbcTemplate jdbc,BesuExecutionReservationService reservations) {
        this(new SettlementClaimWorkReads(jdbc),reservations);
    }
    BesuReservationWorker(SettlementClaimWorkReads reads,BesuExecutionReservationService reservations) {
        this.reads=reads;this.reservations=reservations;
    }
    @Scheduled(fixedDelayString="${app.besu.funding-workers.poll-ms:5000}")
    public synchronized void poll() {
        try {
            var rows=reads.awaitingReservation(cursor);
            for(var id:rows) {
                cursor=id;
                try { reservations.reserve(reads.execution(id)); }
                catch(RuntimeException failure) { deferred("reservation",id); }
            }
            if(rows.size()<SettlementClaimWorkReads.BATCH_SIZE)cursor=null;
        } catch(RuntimeException failure) { deferred("scan",null); }
    }
    private static void deferred(String phase,UUID id) {
        LoggerFactory.getLogger(BesuReservationWorker.class).warn(
            "service=blockchain-service event=besu_reservation_worker_deferred phase={} executionId={}",phase,id);
    }
}
