package com.cognizant.stablecoins.flow;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Bounded discovery of funded, receipt-accounted private transfers; no closure authority. */
public final class BesuClosureWorkReads {
    public static final int BATCH_SIZE=10;
    private final JdbcTemplate jdbc;
    public BesuClosureWorkReads(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Candidate> pending(UUID after,boolean reconciliationOnly) {
        return jdbc.query("""
            select f.execution_id,f.execution_document::text,f.execution_fingerprint
            from payment_besu_funding f join payment p on p.payment_id=f.payment_id
            join besu_signed_submission s on s.execution_id=f.execution_id
            join besu_ledger_receipt a on a.execution_id=f.execution_id
            where s.status='CONFIRMED'
              and p.status in ('FUNDED','BLOCKCHAIN_SUBMITTED','BLOCKCHAIN_CONFIRMED','SETTLEMENT_COMPLETED','RECONCILIATION_PENDING')
              and (?=false or p.status='RECONCILIATION_PENDING')
              and (cast(? as uuid) is null or f.execution_id>cast(? as uuid))
            order by f.execution_id limit ?
            """,(r,n)->new Candidate(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),reconciliationOnly,after,after,BATCH_SIZE);
    }
    public record Candidate(UUID id,String document,String fingerprint) {
        public SettlementBinding.Execution execution() {
            var e=SettlementBindingJson.read(document,SettlementBinding.Execution.class);
            if(!id.equals(e.executionId()) || !SettlementBindingJson.fingerprint(e).equals(fingerprint)
                || e.context().token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
                || !"DEMOUSD".equals(e.context().token().symbol()))throw new IllegalStateException("BESU_CLOSURE_WORK_CONFLICT");
            return e;
        }
    }
}
