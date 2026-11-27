package com.cognizant.stablecoins.flow;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/**
 * ADR-007 read-only payment-owner claim boundary. Terms supplied by a caller
 * are not authority. Only the immutable projection written atomically with the
 * payment owner's independently approved claim can authorize a reservation.
 * Historical claims without the projection fail closed; no inferred/backfilled authority.
 */
public final class SettlementExecutionReads {
    private final JdbcTemplate jdbc;
    public SettlementExecutionReads(JdbcTemplate jdbc) { this.jdbc=Objects.requireNonNull(jdbc); }

    public void requireClaimed(SettlementBinding.Execution expected) {
        Objects.requireNonNull(expected);
        var rows=jdbc.query("select * from payment_settlement_execution_claim where execution_id=?",
                (rs,n)->{
                    var e=SettlementBindingJson.read(rs.getString("execution_document"),SettlementBinding.Execution.class);
                    var i=e.instruction(); var r=e.review();
                    if (!e.equals(expected)
                            || !SettlementBindingJson.fingerprint(e).equals(rs.getString("execution_fingerprint"))
                            || !e.executionId().equals(rs.getObject("execution_id",UUID.class))
                            || !i.paymentId().equals(rs.getString("payment_id"))
                            || !i.correlationId().equals(rs.getString("correlation_id"))
                            || !i.customerId().equals(rs.getObject("customer_id",UUID.class))
                            || !i.intentId().equals(rs.getObject("instruction_id",UUID.class))
                            || !r.recommendationId().equals(rs.getObject("recommendation_id",UUID.class))
                            || r.version()!=rs.getInt("recommendation_version")
                            || !r.quoteId().equals(rs.getObject("quote_id",UUID.class))
                            || r.quoteVersion()!=rs.getInt("quote_version")
                            || !r.preparationId().equals(rs.getObject("preparation_id",UUID.class))
                            || i.context().token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
                            || !"DEMOUSD".equals(i.context().token().symbol()))
                        throw conflict();
                    r.requireFresh(rs.getTimestamp("claimed_at").toInstant());
                    return e;
                },expected.executionId());
        if (rows.size()!=1) throw conflict();
    }
    private static IllegalStateException conflict() {
        return new IllegalStateException("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
    }
}