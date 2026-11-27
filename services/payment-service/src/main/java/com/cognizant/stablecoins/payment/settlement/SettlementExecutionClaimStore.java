package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.*;

/** Owner-only DAO. No bean/endpoint; caller must authorize and lock payment in this transaction. */
final class SettlementExecutionClaimStore {
    private final JdbcTemplate jdbc;
    SettlementExecutionClaimStore(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

    Optional<SettlementExecutionClaim> byPayment(String paymentId) {
        return find("payment_id", paymentId);
    }
    Optional<SettlementExecutionClaim> byId(UUID executionId) { return find("execution_id", executionId); }

    private Optional<SettlementExecutionClaim> find(String column, Object key) {
        if (!Set.of("payment_id", "execution_id").contains(column)) throw new IllegalArgumentException();
        return jdbc.query("select * from payment_settlement_execution_claim where " + column + "=?",
                (rs, index) -> {
                    var claim = SettlementExecutionClaimJson.read(rs.getString("document"), SettlementExecutionClaim.class);
                    var e = claim.execution(); var r = e.review(); var i = e.instruction();
                    if (!e.executionId().equals(rs.getObject("execution_id",UUID.class))
                            || !i.paymentId().equals(rs.getString("payment_id"))
                            || !i.correlationId().equals(rs.getString("correlation_id"))
                            || !i.customerId().equals(rs.getObject("customer_id",UUID.class))
                            || !i.intentId().equals(rs.getObject("instruction_id",UUID.class))
                            || !r.recommendationId().equals(rs.getObject("recommendation_id",UUID.class))
                            || r.version()!=rs.getInt("recommendation_version")
                            || !r.quoteId().equals(rs.getObject("quote_id",UUID.class))
                            || r.quoteVersion()!=rs.getInt("quote_version")
                            || !r.preparationId().equals(rs.getObject("preparation_id",UUID.class))
                            || !claim.requestedBy().equals(rs.getString("actor_id"))
                            || !claim.claimedAt().equals(rs.getTimestamp("claimed_at").toInstant())
                            || !SettlementExecutionClaimJson.fingerprint(claim).equals(rs.getString("fingerprint")))
                        throw new IllegalStateException("SETTLEMENT_CLAIM_INTEGRITY_FAILED");
                    return claim;
                }, key).stream().findFirst();
    }

    void capture(SettlementExecutionClaim claim) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("PAYMENT_OWNER_TRANSACTION_REQUIRED");
        var e=claim.execution(); var r=e.review(); var i=e.instruction();
        var owners=jdbc.queryForList("select p.correlation_id,a.customer_id from payment p join account a on a.account_id=p.sender_account_id where p.payment_id=? for update of p",i.paymentId());
        if (owners.size()!=1 || !i.correlationId().equals(owners.getFirst().get("correlation_id"))
                || !i.customerId().equals(owners.getFirst().get("customer_id")))
            throw new IllegalStateException("SETTLEMENT_PAYMENT_SCOPE_MISMATCH");
        int inserted=jdbc.update("insert into payment_settlement_execution_claim(execution_id,payment_id,correlation_id,customer_id,instruction_id,recommendation_id,recommendation_version,quote_id,quote_version,preparation_id,actor_id,claimed_at,document,fingerprint,execution_document,execution_fingerprint) values (?,?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?,cast(? as jsonb),?) on conflict do nothing",
                e.executionId(),i.paymentId(),i.correlationId(),i.customerId(),i.intentId(),r.recommendationId(),r.version(),
                r.quoteId(),r.quoteVersion(),r.preparationId(),claim.requestedBy(),java.sql.Timestamp.from(claim.claimedAt()),
                SettlementExecutionClaimJson.write(claim),SettlementExecutionClaimJson.fingerprint(claim),
                SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));
        if (byId(e.executionId()).filter(claim::equals).isEmpty())
            throw new IllegalStateException("SETTLEMENT_EXECUTION_CLAIM_CONFLICT");
        if (inserted==1) jdbc.update("insert into routing_audit(id,recommendation_id,payment_id,correlation_id,actor_id,action,recorded_at) values (?,?,?,?,?,'SETTLEMENT_EXECUTION_CLAIMED',?)",
                UUID.randomUUID(),r.recommendationId(),i.paymentId(),i.correlationId(),claim.requestedBy(),java.sql.Timestamp.from(claim.claimedAt()));
    }
}