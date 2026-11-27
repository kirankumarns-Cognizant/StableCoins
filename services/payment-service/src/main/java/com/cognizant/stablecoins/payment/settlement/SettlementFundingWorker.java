package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.SettlementClaimWorkReads;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/** Payment owner only. No broad worker transaction, blockchain writes or automatic minting. */
@Component
@ConditionalOnProperty(name={"app.flow.enabled","app.besu.funding.enabled","app.besu.funding-workers.enabled"},havingValue="true")
public class SettlementFundingWorker {
    private final SettlementClaimWorkReads reads;
    private final SettlementClaimedFundingService funding;
    private UUID cursor;
    @Autowired
    public SettlementFundingWorker(JdbcTemplate jdbc,SettlementClaimedFundingService funding) {
        this(new SettlementClaimWorkReads(jdbc),funding);
    }
    SettlementFundingWorker(SettlementClaimWorkReads reads,SettlementClaimedFundingService funding) {
        this.reads=reads;this.funding=funding;
    }
    @Scheduled(fixedDelayString="${app.besu.funding-workers.poll-ms:5000}")
    public synchronized void poll() {
        try {
            var rows=reads.awaitingFunding(cursor);
            for(var id:rows) {
                cursor=id;
                try { funding.fundClaimed(id); }
                catch(RuntimeException failure) { deferred("funding",id); }
            }
            if(rows.size()<SettlementClaimWorkReads.BATCH_SIZE)cursor=null;
        } catch(RuntimeException failure) { deferred("scan",null); }
    }
    private static void deferred(String phase,UUID id) {
        LoggerFactory.getLogger(SettlementFundingWorker.class).warn(
            "service=payment-service event=besu_funding_worker_deferred phase={} executionId={}",phase,id);
    }
}
