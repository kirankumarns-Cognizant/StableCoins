package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** Payment-owner immutable quote drafts, separate from approved V13 review bindings. */
final class SettlementQuoteStore {
    private final JdbcTemplate jdbc;
    SettlementQuoteStore(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

    boolean executionClaimed(String paymentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from payment_settlement_execution_claim where payment_id=?)",Boolean.class,paymentId));
    }

    Optional<SettlementQuote> find(UUID id) {
        var rows = jdbc.query("select quote_id,request_id,instruction_id,payment_id,correlation_id,document::text,fingerprint from payment_settlement_quote where review_id=?",
                (rs, n) -> {
                    var q = SettlementBindingJson.read(rs.getString(6), SettlementQuote.class);
                    var r = q.review(); var i = r.instruction();
                    if (!id.equals(r.recommendationId()) || !r.quoteId().equals(rs.getObject(1, UUID.class))
                            || !r.preparationId().equals(rs.getObject(2, UUID.class))
                            || !i.intentId().equals(rs.getObject(3, UUID.class))
                            || !i.paymentId().equals(rs.getString(4))
                            || !i.correlationId().equals(rs.getString(5))
                            || !SettlementBindingJson.fingerprint(q).equals(rs.getString(7)))
                        throw new IllegalStateException("SETTLEMENT_QUOTE_INTEGRITY_FAILED");
                    return q;
                }, id);
        if (rows.size() > 1) throw new IllegalStateException("SETTLEMENT_QUOTE_INTEGRITY_FAILED");
        return rows.stream().findFirst();
    }

    void capture(SettlementQuote quote, String actorId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("PAYMENT_OWNER_TRANSACTION_REQUIRED");
        var r = quote.review(); var i = r.instruction();
        var owners = jdbc.queryForList("select p.correlation_id,a.customer_id from payment p join account a on a.account_id=p.sender_account_id where p.payment_id=? for update of p", i.paymentId());
        if (owners.size() != 1 || !i.customerId().equals(owners.getFirst().get("customer_id"))
                || !i.correlationId().equals(owners.getFirst().get("correlation_id")))
            throw new IllegalStateException("SETTLEMENT_PAYMENT_SCOPE_MISMATCH");
        jdbc.update("insert into payment_settlement_quote(review_id,quote_id,request_id,instruction_id,payment_id,correlation_id,actor_id,document,fingerprint) values (?,?,?,?,?,?,?,cast(? as jsonb),?) on conflict(review_id) do nothing",
                r.recommendationId(), r.quoteId(), r.preparationId(), i.intentId(), i.paymentId(),
                i.correlationId(), actorId, SettlementBindingJson.write(quote), SettlementBindingJson.fingerprint(quote));
        if (find(r.recommendationId()).filter(quote::equals).isEmpty())
            throw new IllegalStateException("SETTLEMENT_QUOTE_KEY_CONFLICT");
    }
}
