package com.cognizant.stablecoins.flow;

import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.Objects;

/** Blockchain-owned reservation evidence, read only. Never creates capacity or authorizes signing. */
public class SettlementReservationReads {
    private final JdbcTemplate jdbc;
    public SettlementReservationReads(JdbcTemplate jdbc) { this.jdbc=Objects.requireNonNull(jdbc); }
    public void requireFreshPinned(SettlementBinding.Execution e,Instant now) {
        e.review().requireFresh(now);
        var rows=jdbc.query("""
                select c.document::text execution_document,c.fingerprint execution_fingerprint,c.payment_id,
                       c.scope_key,c.token_contract,c.status,c.expires_at,c.pinned_at,
                       r.document::text observation_document,r.fingerprint observation_fingerprint,
                       exists(select 1 from besu_signed_submission s where s.execution_id=c.execution_id) signed,
                       exists(select 1 from besu_final_receipt f where f.execution_id=c.execution_id) resolved
                from besu_liquidity_commitment c join besu_execution_reservation r using(execution_id)
                where c.execution_id=?
                """,(rs,n)->{
            var saved=SettlementBindingJson.read(rs.getString("execution_document"),SettlementBinding.Execution.class);
            var o=SettlementBindingJson.read(rs.getString("observation_document"),SettlementObservation.class);
            var pinned=rs.getTimestamp("pinned_at");
            if(!e.equals(saved) || !SettlementBindingJson.fingerprint(e).equals(rs.getString("execution_fingerprint"))
                    || !e.instruction().paymentId().equals(rs.getString("payment_id"))
                    || !e.context().nonceScope().equals(rs.getString("scope_key"))
                    || !e.context().token().contract().equals(rs.getString("token_contract"))
                    || !"PINNED".equals(rs.getString("status"))
                    || !e.review().expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS).equals(rs.getTimestamp("expires_at").toInstant())
                    || !SettlementBindingJson.fingerprint(o).equals(rs.getString("observation_fingerprint"))
                    || pinned==null || pinned.toInstant().isBefore(o.observedAt()) || pinned.toInstant().isAfter(now)
                    || o.observedAt().isBefore(e.review().assessedAt())
                    || e.review().nativeFeeReserve().compareTo(o.executionFeeEstimate())<0
                    || o.nativeTotal().compareTo(e.review().nativeFeeReserve())<0
                    || !o.totalsCoverEstimate() || rs.getBoolean("signed") || rs.getBoolean("resolved"))
                throw conflict();
            o.requireFor(e.instruction(),now);
            return o;
        },e.executionId());
        if(rows.size()!=1) throw conflict();
    }
    private static IllegalStateException conflict(){return new IllegalStateException("FRESH_UNSIGNED_BESU_RESERVATION_REQUIRED");}
}
