package com.cognizant.stablecoins.payment.flow;

import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** Immutable terminal payment-owner acknowledgement; the caller holds the payment lock. */
final class BesuPaymentClosureStore {
    private final JdbcTemplate jdbc;
    BesuPaymentClosureStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    Optional<BesuSettlementClosureReads.Report> find(SettlementBinding.Execution e) {
        var rows=jdbc.query("select execution_document::text,execution_fingerprint,payment_id,correlation_id,report_id,source_hash from payment_besu_closure where execution_id=?",
            (r,n)->{
                if(!e.equals(SettlementBindingJson.read(r.getString(1),SettlementBinding.Execution.class))
                    || !SettlementBindingJson.fingerprint(e).equals(r.getString(2)) || !e.instruction().paymentId().equals(r.getString(3))
                    || !e.instruction().correlationId().equals(r.getString(4)))throw new IllegalStateException("BESU_CLOSURE_BINDING_CONFLICT");
                return new BesuSettlementClosureReads.Report(r.getObject(5,UUID.class),r.getString(6));
            },e.executionId());
        if(rows.size()>1)throw new IllegalStateException("BESU_CLOSURE_BINDING_CONFLICT");return rows.stream().findFirst();
    }
    void complete(SettlementBinding.Execution e,BesuSettlementClosureReads.Report report) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PAYMENT_OWNER_TRANSACTION_REQUIRED");
        jdbc.update("insert into payment_besu_closure(execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,report_id,source_hash) values (?,?,?,cast(? as jsonb),?,?,?)",
            e.executionId(),e.instruction().paymentId(),e.instruction().correlationId(),SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e),report.id(),report.sourceHash());
    }
}
