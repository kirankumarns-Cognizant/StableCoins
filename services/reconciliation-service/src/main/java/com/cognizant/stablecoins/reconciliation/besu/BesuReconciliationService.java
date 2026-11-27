package com.cognizant.stablecoins.reconciliation.besu;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.reconciliation.flow.ReconciliationService.Report;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Internal, gated report writer. No listener, endpoint, owner-state update or execution command. */
@Service
@ConditionalOnProperty(name="app.besu.reconciliation.enabled",havingValue="true")
public class BesuReconciliationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final BesuClosureEvidence checker;
    private final SettlementExecutionReads claims;
    @org.springframework.beans.factory.annotation.Autowired
    public BesuReconciliationService(JdbcTemplate jdbc,ObjectMapper mapper,Environment env) {
        this(jdbc,mapper,new BesuClosureEvidence(jdbc,mapper,env),new SettlementExecutionReads(jdbc));
    }
    BesuReconciliationService(JdbcTemplate jdbc,ObjectMapper mapper,BesuClosureEvidence checker,SettlementExecutionReads claims) {
        this.jdbc=jdbc;this.mapper=mapper;this.checker=checker;this.claims=claims;
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Report run(UUID reportId,SettlementBinding.Execution execution) {
        Objects.requireNonNull(reportId);Objects.requireNonNull(execution);claims.requireClaimed(execution);
        var prior=find(reportId);
        if(prior.isPresent())return verify(prior.get(),execution);
        return persist(reportId,execution,checker.compare(checker.capture(execution)));
    }
    /** Fresh sources get a new stable report identity; retries of those sources reuse it. */
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Report runCurrent(SettlementBinding.Execution execution) {
        claims.requireClaimed(execution);
        var comparison=checker.compare(checker.capture(execution));
        var document=BesuReconciliationDocuments.document(mapper,execution,comparison);
        var id=BesuReconciliationDocuments.id(mapper,document);
        var prior=find(id);
        if(prior.isPresent()) {
            var report=verify(prior.get(),execution);
            if(!report.sourceHash().equals(digest(document)))throw new IllegalStateException("BESU_RECONCILIATION_SOURCE_CONFLICT");
            return report;
        }
        return persist(id,execution,comparison);
    }
    private Report persist(UUID reportId,SettlementBinding.Execution execution,BesuClosureEvidence.Comparison comparison) {
        var document=BesuReconciliationDocuments.document(mapper,execution,comparison);
        var i=execution.instruction();
        int inserted=jdbc.update("insert into reconciliation(reconciliation_id,execution_id,payment_id,correlation_id,status,source_hash,document) values (?,?,?,?,?,?,cast(? as jsonb)) on conflict(reconciliation_id) do nothing",
            reportId,execution.executionId(),i.paymentId(),i.correlationId(),comparison.status(),digest(document),FlowJson.json(mapper,document));
        if(inserted==0)return verify(find(reportId).orElseThrow(),execution);
        for(String finding:comparison.findings())jdbc.update("insert into reconciliation_detail(reconciliation_id,finding) values (?,?)",reportId,finding);
        return verify(find(reportId).orElseThrow(),execution);
    }
    private Report verify(Report r,SettlementBinding.Execution e) {
        var d=r.evidence();
        if(!r.executionId().equals(e.executionId()) || !r.paymentId().equals(e.instruction().paymentId())
            || !r.correlationId().equals(e.instruction().correlationId()) || !BesuClosureEvidence.KIND.equals(d.path("kind").asText())
            || !BesuClosureEvidence.GATE.equals(d.path("closureGate").asText()) || !r.status().equals(d.path("status").asText())
            || !SettlementBindingJson.write(e).equals(d.path("executionDocument").asText())
            || !SettlementBindingJson.fingerprint(e).equals(d.path("executionFingerprint").asText()) || !r.sourceHash().equals(digest(d)))
            throw new IllegalStateException("BESU_RECONCILIATION_KEY_OR_EVIDENCE_CONFLICT");
        return r;
    }
    private String digest(JsonNode document){return FlowJson.fingerprint(mapper,BesuClosureEvidence.canonical(document));}
    private Optional<Report> find(UUID id) {
        return jdbc.query("select * from reconciliation where reconciliation_id=?",(r,n)->new Report(r.getObject("reconciliation_id",UUID.class),r.getObject("execution_id",UUID.class),r.getString("payment_id"),r.getString("correlation_id"),r.getString("status"),r.getString("source_hash"),FlowJson.value(mapper,r.getString("document"),JsonNode.class),r.getTimestamp("created_at").toInstant()),id).stream().findFirst();
    }
}
