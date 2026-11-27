package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Read-only discovery; only the accounting owner may authorize and commit postings. */
final class BesuAccountingWorkReads {
    static final int BATCH_SIZE=10;
    private final JdbcTemplate jdbc;
    BesuAccountingWorkReads(JdbcTemplate jdbc) { this.jdbc=Objects.requireNonNull(jdbc); }

    List<Candidate> pending(UUID after) {
        // Include terminal submissions: a restart may first see the transfer after mining.
        // Do not depend on mutable payment state, quote expiry or submission enablement.
        return jdbc.query("""
            select s.execution_id,s.document::text,s.fingerprint,s.status
            from besu_signed_submission s
            where s.status in ('ATTEMPTED','UNKNOWN','SUBMITTED','CONFIRMED','REVERTED')
              and not exists(select 1 from besu_ledger_receipt a where a.execution_id=s.execution_id)
              and (cast(? as uuid) is null or s.execution_id>cast(? as uuid))
            order by s.execution_id limit ?
            """,(rs,n)->new Candidate(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4)),
            after,after,BATCH_SIZE);
    }
    record Candidate(UUID id,String document,String fingerprint,String status) {
        SettlementBinding.Execution execution() {
            var e=SettlementBindingJson.read(document,SettlementBinding.Execution.class);
            if(!e.executionId().equals(id) || !SettlementBindingJson.fingerprint(e).equals(fingerprint)
                    || !Set.of("ATTEMPTED","UNKNOWN","SUBMITTED","CONFIRMED","REVERTED").contains(status))
                throw new IllegalStateException("BESU_ACCOUNTING_WORK_CONFLICT");
            BesuAccountingEvidence.requirePrivate(e);
            return e;
        }
        boolean terminal() {return Set.of("CONFIRMED","REVERTED").contains(status);}
    }
}
