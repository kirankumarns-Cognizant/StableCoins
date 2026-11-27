package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Bounded discovery only. A row is not authority; owner services revalidate it under their locks. */
final class BesuExecutionWorkReads {
    static final int BATCH_SIZE=10;
    private final JdbcTemplate jdbc;
    BesuExecutionWorkReads(JdbcTemplate jdbc) { this.jdbc=Objects.requireNonNull(jdbc); }

    List<Candidate> recovering(UUID after) {
        // Independent of current payment state, funding expiry and new-submission enablement.
        // Do not read signed bytes here or export them to scheduler/logging code.
        return jdbc.query("""
            select execution_id,document::text,fingerprint,status from besu_signed_submission
            where status in ('SIGNED','ATTEMPTED','UNKNOWN','SUBMITTED')
              and (cast(? as uuid) is null or execution_id>cast(? as uuid))
            order by execution_id limit ?
            """,(rs,n)->new Candidate(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4)),
            after,after,BATCH_SIZE);
    }
    List<Candidate> funded(UUID after) {
        // ADR-007 cross-owner read; no update or lock of payment/ledger-owned rows.
        return jdbc.query("""
            select f.execution_id,f.execution_document::text,f.execution_fingerprint,'FUNDED' from payment_besu_funding f
            join payment p on p.payment_id=f.payment_id
            where p.status='FUNDED'
              and not exists(select 1 from besu_signed_submission s where s.execution_id=f.execution_id)
              and (cast(? as uuid) is null or f.execution_id>cast(? as uuid))
            order by f.execution_id limit ?
            """,(rs,n)->new Candidate(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4)),
            after,after,BATCH_SIZE);
    }
    record Candidate(UUID id,String document,String fingerprint,String status) {
        SettlementBinding.Execution execution() {
            var e=SettlementBindingJson.read(document,SettlementBinding.Execution.class);
            if (!e.executionId().equals(id) || !SettlementBindingJson.fingerprint(e).equals(fingerprint)
                    || e.context().token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
                    || !"DEMOUSD".equals(e.context().token().symbol()))
                throw new IllegalStateException("BESU_WORK_BINDING_CONFLICT");
            return e;
        }
    }
}
