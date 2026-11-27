package com.cognizant.stablecoins.flow;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Read-only discovery of already authorized claims. Discovery never grants execution authority. */
public final class SettlementClaimWorkReads {
    public static final int BATCH_SIZE=10;
    private final JdbcTemplate jdbc;
    public SettlementClaimWorkReads(JdbcTemplate jdbc) { this.jdbc=Objects.requireNonNull(jdbc); }

    public List<UUID> awaitingReservation(UUID after) { return pending(after,false); }
    public List<UUID> awaitingFunding(UUID after) { return pending(after,true); }

    private List<UUID> pending(UUID after,boolean reserved) {
        String reservation=reserved
            ? "exists(select 1 from besu_execution_reservation r join besu_liquidity_commitment l using(execution_id) where r.execution_id=c.execution_id and l.status='PINNED')"
            : "not exists(select 1 from besu_execution_reservation r where r.execution_id=c.execution_id)";
        return jdbc.query("""
            select c.execution_id from payment_settlement_execution_claim c
            join payment p on p.payment_id=c.payment_id
            where p.status='COMPLIANCE_APPROVED' and c.execution_document is not null
              and not exists(select 1 from payment_besu_funding f where f.execution_id=c.execution_id)
              and not exists(select 1 from besu_signed_submission s where s.execution_id=c.execution_id)
              and
            """+reservation+"""
              and (cast(? as uuid) is null or c.execution_id>cast(? as uuid))
            order by c.execution_id limit ?
            """,(rs,n)->rs.getObject(1,UUID.class),after,after,BATCH_SIZE);
    }

    /** The blockchain owner rechecks the complete canonical claim before reserving. */
    public SettlementBinding.Execution execution(UUID id) {
        var rows=jdbc.query("""
            select execution_document::text,execution_fingerprint from payment_settlement_execution_claim
            where execution_id=?
            """,(rs,n)->{
                var e=SettlementBindingJson.read(rs.getString(1),SettlementBinding.Execution.class);
                if(!e.executionId().equals(id) || !SettlementBindingJson.fingerprint(e).equals(rs.getString(2))
                        || e.context().token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
                        || !"DEMOUSD".equals(e.context().token().symbol()))
                    throw new IllegalStateException("BESU_CLAIM_WORK_BINDING_CONFLICT");
                return e;
            },id);
        if(rows.size()!=1)throw new IllegalStateException("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
        return rows.getFirst();
    }
}
