package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Gated restart-safe owner worker. No broad transaction or cross-owner financial write. */
@Component
@ConditionalOnProperty(name={"app.besu.enabled","app.besu.workers.enabled"},havingValue="true")
public class BesuExecutionWorker {
    private final BesuExecutionWorkReads reads;
    private final SettlementFundingReads funding;
    private final BesuSubmissionService submissions;
    private final BesuReceiptService receipts;
    private final Environment env;
    private UUID recoveryCursor;
    private UUID fundingCursor;

    @Autowired
    public BesuExecutionWorker(JdbcTemplate jdbc,ObjectMapper mapper,BesuSubmissionService submissions,
                               BesuReceiptService receipts,Environment env) {
        this(new BesuExecutionWorkReads(jdbc),new SettlementFundingReads(jdbc,mapper,new BesuClosureEvidence.Policy(
            env.getProperty("app.ledger.accounting.version","adr015-v1"),
            env.getProperty("app.ledger.accounting.accounts.funding-usd","110002"),
            env.getProperty("app.ledger.accounting.accounts.corporate-usd","110001"),
            env.getProperty("app.ledger.accounting.accounts.conversion-usd","110003"))),submissions,receipts,env);
    }
    BesuExecutionWorker(BesuExecutionWorkReads reads,SettlementFundingReads funding,BesuSubmissionService submissions,
                        BesuReceiptService receipts,Environment env) {
        this.reads=reads;this.funding=funding;this.submissions=submissions;this.receipts=receipts;this.env=env;
    }

    @Scheduled(fixedDelayString="${app.besu.workers.poll-ms:5000}")
    public synchronized void poll() {
        // Each lane progresses independently; unavailable funding does not stop saved-hash recovery.
        try {
            var rows=reads.recovering(recoveryCursor);
            for(var row:rows) { recoveryCursor=row.id(); process(row); }
            if(rows.size()<BesuExecutionWorkReads.BATCH_SIZE) recoveryCursor=null;
        } catch(RuntimeException failure) { deferred("recovery_scan",null); }
        if(!env.getProperty("app.besu.workers.submission-enabled",Boolean.class,false)) return;
        try {
            var rows=reads.funded(fundingCursor);
            for(var row:rows) { fundingCursor=row.id(); process(row); }
            if(rows.size()<BesuExecutionWorkReads.BATCH_SIZE) fundingCursor=null;
        } catch(RuntimeException failure) { deferred("funding_scan",null); }
    }
    private void process(BesuExecutionWorkReads.Candidate row) {
        try {
            var e=row.execution();
            boolean first=Set.of("FUNDED","SIGNED").contains(row.status());
            if(first) {
                if(!env.getProperty("app.besu.workers.submission-enabled",Boolean.class,false)) return;
                funding.requireAllocated(e); // Wait for exact committed ledger allocation before entering signer work.
                submissions.submit(e); // Existing owner fences persist signature and attempt before any broadcast.
            } else {
                if(!Set.of("ATTEMPTED","UNKNOWN","SUBMITTED").contains(row.status()))
                    throw new IllegalStateException("BESU_WORK_STATUS_INVALID");
                submissions.observeSubmission(e); // No first attempt or re-sign, even if new work is disabled.
            }
            receipts.confirm(e); // Existing receipt owner checks exact chain/log/finality; no financial postings.
        } catch(RuntimeException failure) { deferred("execution",row.id()); }
    }
    private static void deferred(String phase,UUID executionId) {
        // Never log candidate documents, signed payloads, exception text or RPC provider details.
        LoggerFactory.getLogger(BesuExecutionWorker.class).warn(
            "service=blockchain-service event=besu_worker_deferred phase={} executionId={}",phase,executionId);
    }
}
