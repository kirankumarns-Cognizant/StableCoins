package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Timestamp;
import java.util.*;

/** Payment-owned immutable debit acknowledgement; caller owns payment and account locks. */
final class SettlementFundingStore {
    private final JdbcTemplate jdbc;
    SettlementFundingStore(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}
    Optional<SettlementFundingService.Receipt> find(SettlementBinding.Execution e) {
        var rows=jdbc.query("select * from payment_besu_funding where execution_id=?",(rs,n)->{
            var saved=SettlementBindingJson.read(rs.getString("execution_document"),SettlementBinding.Execution.class);
            var receipt=new SettlementFundingService.Receipt(saved,rs.getObject("account_id",UUID.class),
                    rs.getBigDecimal("balance_before"),rs.getBigDecimal("balance_after"),rs.getString("actor_id"),
                    UUID.fromString(rs.getString("event_id")),rs.getTimestamp("funded_at").toInstant());
            if(!e.equals(saved) || !e.executionId().equals(rs.getObject("execution_id",UUID.class))
                    || !e.instruction().paymentId().equals(rs.getString("payment_id"))
                    || !e.instruction().customerId().equals(rs.getObject("customer_id",UUID.class))
                    || !e.instruction().correlationId().equals(rs.getString("correlation_id"))
                    || e.instruction().sourceUsd().compareTo(rs.getBigDecimal("source_usd"))!=0
                    || !receipt.fundingReference().equals(rs.getString("funding_reference"))
                    || !SettlementBindingJson.fingerprint(e).equals(rs.getString("execution_fingerprint"))) throw conflict();
            return receipt;
        },e.executionId());
        if(rows.size()>1)throw conflict();return rows.stream().findFirst();
    }
    void capture(SettlementFundingService.Receipt r) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("PAYMENT_OWNER_TRANSACTION_REQUIRED");
        var e=r.execution();var i=e.instruction();
        jdbc.update("""
                insert into payment_besu_funding(execution_id,payment_id,correlation_id,customer_id,account_id,source_usd,
                  balance_before,balance_after,actor_id,event_id,funded_at,funding_reference,execution_document,execution_fingerprint)
                values (?,?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?)
                """,e.executionId(),i.paymentId(),i.correlationId(),i.customerId(),r.accountId(),i.sourceUsd(),r.balanceBefore(),
                r.balanceAfter(),r.actorId(),r.eventId().toString(),Timestamp.from(r.fundedAt()),r.fundingReference(),
                SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));
        if(find(e).filter(saved->saved.accountId().equals(r.accountId()) && saved.eventId().equals(r.eventId())
                && saved.balanceBefore().compareTo(r.balanceBefore())==0 && saved.balanceAfter().compareTo(r.balanceAfter())==0
                && saved.actorId().equals(r.actorId()) && saved.fundedAt().equals(r.fundedAt())).isEmpty()) throw conflict();
    }
    private static IllegalStateException conflict(){return new IllegalStateException("BESU_FUNDING_BINDING_CONFLICT");}
}
