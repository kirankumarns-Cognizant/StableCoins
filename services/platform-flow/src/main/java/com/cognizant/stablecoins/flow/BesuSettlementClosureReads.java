package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Cross-owner reads only. A chain/ledger report alone never authorizes payment closure. */
public final class BesuSettlementClosureReads {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    private final BesuClosureEvidence checker;private final SettlementFundingReads funding;
    public BesuSettlementClosureReads(JdbcTemplate jdbc,ObjectMapper mapper,Environment env) {
        this(jdbc,mapper,new BesuClosureEvidence(jdbc,mapper,env),new SettlementFundingReads(jdbc,mapper,
            new BesuClosureEvidence.Policy(env.getProperty("app.ledger.accounting.version","adr015-v1"),
            env.getProperty("app.ledger.accounting.accounts.funding-usd","110002"),
            env.getProperty("app.ledger.accounting.accounts.corporate-usd","110001"),
            env.getProperty("app.ledger.accounting.accounts.conversion-usd","110003"))));
    }
    BesuSettlementClosureReads(JdbcTemplate jdbc,ObjectMapper mapper,BesuClosureEvidence checker,SettlementFundingReads funding) {
        this.jdbc=jdbc;this.mapper=mapper;this.checker=checker;this.funding=funding;
    }
    public BesuClosureEvidence.Comparison current(SettlementBinding.Execution e) {
        funding.requireAllocated(e); // Includes the exact committed simulated debit/outbox, not a claim alone.
        return checker.compare(checker.capture(e));
    }
    public record Report(UUID id,String sourceHash) {
        public Report {if(id==null || sourceHash==null || !sourceHash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("INVALID_BESU_REPORT_REFERENCE");}
    }
    public Optional<Report> matchingReport(SettlementBinding.Execution e,BesuClosureEvidence.Comparison comparison) {
        if(!"MATCHED".equals(comparison.status()) || !comparison.findings().isEmpty()
                || !BesuClosureEvidence.KIND.equals(comparison.kind()) || !BesuClosureEvidence.GATE.equals(comparison.closureGate()))
            throw new IllegalStateException("BESU_COMPLETE_EVIDENCE_REQUIRED");
        var expected=BesuReconciliationDocuments.document(mapper,e,comparison);
        UUID id=BesuReconciliationDocuments.id(mapper,expected);String hash=BesuReconciliationDocuments.hash(mapper,expected);
        return jdbc.query("select execution_id,payment_id,correlation_id,status,source_hash,document::text from reconciliation where reconciliation_id=?",
            (r,n)->{
                var actual=FlowJson.value(mapper,r.getString(6),JsonNode.class);
                if(!e.executionId().equals(r.getObject(1,UUID.class)) || !e.instruction().paymentId().equals(r.getString(2))
                    || !e.instruction().correlationId().equals(r.getString(3)) || !"MATCHED".equals(r.getString(4))
                    || !hash.equals(r.getString(5)) || !hash.equals(BesuReconciliationDocuments.hash(mapper,actual))
                    || !BesuClosureEvidence.canonical(expected).equals(BesuClosureEvidence.canonical(actual)))
                    throw new IllegalStateException("BESU_FRESH_RECONCILIATION_REQUIRED");
                return new Report(id,hash);
            },id).stream().findFirst();
    }
}
