package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static com.cognizant.stablecoins.flow.BesuClosureEvidence.*;
import static com.cognizant.stablecoins.flow.LifecycleReadModels.*;

/** Conclusive failed-transfer evidence. Never means delivered, refunded or successfully reconciled. */
public final class BesuRevertEvidence {
    public static final String RELEASE="BLOCKCHAIN_REVERT_RELEASE";
    public static final String ACTION="RELEASE_REVERTED_TOKEN_IN_FLIGHT";
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    private final BesuClosureEvidence checker;private final SettlementFundingReads funding;
    public BesuRevertEvidence(JdbcTemplate jdbc,ObjectMapper mapper,Environment env) {
        this(jdbc,mapper,new BesuClosureEvidence(jdbc,mapper,env),new SettlementFundingReads(jdbc,mapper,
            new Policy(env.getProperty("app.ledger.accounting.version","adr015-v1"),
                env.getProperty("app.ledger.accounting.accounts.funding-usd","110002"),
                env.getProperty("app.ledger.accounting.accounts.corporate-usd","110001"),
                env.getProperty("app.ledger.accounting.accounts.conversion-usd","110003"))));
    }
    BesuRevertEvidence(JdbcTemplate jdbc,ObjectMapper mapper,BesuClosureEvidence checker,SettlementFundingReads funding) {
        this.jdbc=jdbc;this.mapper=mapper;this.checker=checker;this.funding=funding;
    }
    public record Proof(String sourceHash,Snapshot sources,Journal submission,boolean released) {}
    public Proof current(SettlementBinding.Execution e) {
        funding.requireAllocated(e);
        return validate(mapper,checker,checker.capture(e));
    }
    static Proof validate(ObjectMapper mapper,BesuClosureEvidence checker,Snapshot all) {
        var releases=all.journals().stream().filter(j->RELEASE.equals(j.type())).toList();
        var base=new Snapshot(all.execution(),all.payment(),all.submission(),all.receipt(),all.acknowledgement(),
            all.journals().stream().filter(j->!RELEASE.equals(j.type())).toList(),all.accounts(),all.payoutPresent());
        var comparison=checker.compare(base);
        if(!comparison.findings().equals(List.of("TRANSFER_REVERTED_REQUIRES_REVIEW")) || !"MISMATCH".equals(comparison.status())
                || base.receipt()==null || base.receipt().success() || base.submission()==null || !"REVERTED".equals(base.submission().status()))
            throw new IllegalStateException("BESU_CONCLUSIVE_REVERT_REQUIRED");
        var original=base.journals().stream().filter(j->"BLOCKCHAIN_SUBMISSION".equals(j.type())).findFirst().orElseThrow();
        if(releases.size()>1)throw new IllegalStateException("BESU_REVERT_RELEASE_CONFLICT");
        if(!releases.isEmpty()) {
            var release=releases.getFirst();
            var expected=original.entries().stream().map(x->new Entry(x.accountCode(),x.asset(),x.credit(),x.debit())).toList();
            if(!"POSTED".equals(release.status()) || !original.paymentId().equals(release.paymentId())
                || !original.correlationId().equals(release.correlationId()) || !reference(original.journalId()).equals(release.reference())
                || !canonical(original.evidence()).equals(canonical(release.evidence()))
                || !lines(expected).equals(lines(release.entries())))throw new IllegalStateException("BESU_REVERT_RELEASE_CONFLICT");
        }
        var document=BesuReconciliationDocuments.document(mapper,all.execution(),comparison);
        return new Proof(BesuReconciliationDocuments.hash(mapper,document),base,original,!releases.isEmpty());
    }
    private static List<String> lines(List<Entry> rows) {
        return rows.stream().map(x->x.accountCode()+":"+x.asset()+":"+x.debit().stripTrailingZeros().toPlainString()+":"+x.credit().stripTrailingZeros().toPlainString()).sorted().toList();
    }
    public static String reference(UUID original){return "BESU-REVERT:"+original;}
    public SettlementBinding.Execution execution(UUID id) {
        var rows=jdbc.query("select execution_document::text from payment_settlement_execution_claim where execution_id=?",
            (r,n)->SettlementBindingJson.read(r.getString(1),SettlementBinding.Execution.class),id);
        if(rows.size()!=1)throw new IllegalStateException("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
        var e=rows.getFirst();if(!id.equals(e.executionId()))throw new IllegalStateException("BESU_REVERT_BINDING_CONFLICT");
        new SettlementExecutionReads(jdbc).requireClaimed(e);return e;
    }
    public record Authorization(String actorId,String sourceHash) {}
    /** Only durable payment-owner authorization can initiate a ledger compensation. */
    public Authorization authorization(SettlementBinding.Execution e,Proof proof) {
        var rows=jdbc.query("""
            select a.actor_id,a.source_hash,a.action,a.execution_fingerprint,a.execution_document::text,
                   f.source_hash,f.tx_hash,p.status,w.actor_id
            from payment_besu_recovery_authorization a join payment_besu_failure f on f.execution_id=a.execution_id
            join payment p on p.payment_id=f.payment_id join payment_besu_funding w on w.execution_id=a.execution_id
            where a.execution_id=?
            """,(r,n)->{
                if(!proof.sourceHash().equals(r.getString(2)) || !ACTION.equals(r.getString(3))
                    || !SettlementBindingJson.fingerprint(e).equals(r.getString(4))
                    || !e.equals(SettlementBindingJson.read(r.getString(5),SettlementBinding.Execution.class))
                    || !proof.sourceHash().equals(r.getString(6)) || !proof.sources().receipt().transactionHash().equals(r.getString(7))
                    || !"FAILED".equals(r.getString(8)) || r.getString(1)==null || r.getString(1).isBlank() || r.getString(1).equals(r.getString(9)))
                    throw new IllegalStateException("BESU_COMPENSATION_AUTHORIZATION_CONFLICT");
                return new Authorization(r.getString(1),r.getString(2));
            },e.executionId());
        if(rows.size()!=1)throw new IllegalStateException("BESU_COMPENSATION_AUTHORIZATION_REQUIRED");return rows.getFirst();
    }
}
