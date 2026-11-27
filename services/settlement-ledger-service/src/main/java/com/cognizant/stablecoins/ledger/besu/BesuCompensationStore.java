package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Ledger-owned immutable acknowledgement; the authorization belongs to payment-service. */
@Repository
public class BesuCompensationStore {
    private final JdbcTemplate jdbc;
    public BesuCompensationStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Acknowledgement(String sourceHash,String actorId,UUID originalJournal,UUID releaseJournal) {}
    public Optional<Acknowledgement> find(SettlementBinding.Execution e) {
        var rows=jdbc.query("""
            select execution_document::text,execution_fingerprint,source_hash,actor_id,original_journal,release_journal
            from besu_ledger_compensation where execution_id=?
            """,(r,n)->{
                if(!e.equals(SettlementBindingJson.read(r.getString(1),SettlementBinding.Execution.class))
                    || !SettlementBindingJson.fingerprint(e).equals(r.getString(2)))throw new IllegalStateException("BESU_COMPENSATION_BINDING_CONFLICT");
                return new Acknowledgement(r.getString(3),r.getString(4),r.getObject(5,UUID.class),r.getObject(6,UUID.class));
            },e.executionId());
        return rows.stream().findFirst();
    }
    public void acknowledge(SettlementBinding.Execution e,Acknowledgement a) {
        jdbc.update("""
            insert into besu_ledger_compensation(execution_id,payment_id,correlation_id,execution_document,
                execution_fingerprint,source_hash,actor_id,original_journal,release_journal)
            values(?,?,?,?::jsonb,?,?,?,?,?)
            """,e.executionId(),e.instruction().paymentId(),e.instruction().correlationId(),SettlementBindingJson.write(e),
            SettlementBindingJson.fingerprint(e),a.sourceHash(),a.actorId(),a.originalJournal(),a.releaseJournal());
    }
}
