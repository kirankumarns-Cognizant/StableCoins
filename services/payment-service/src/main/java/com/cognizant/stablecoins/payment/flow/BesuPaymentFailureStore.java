package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
final class BesuPaymentFailureStore {
    private final JdbcTemplate jdbc;BesuPaymentFailureStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    Optional<String> failure(SettlementBinding.Execution e) {
        return jdbc.query("select execution_document::text,execution_fingerprint,source_hash from payment_besu_failure where execution_id=?",(r,n)->{
            if(!e.equals(SettlementBindingJson.read(r.getString(1),SettlementBinding.Execution.class))
                || !SettlementBindingJson.fingerprint(e).equals(r.getString(2)))throw new IllegalStateException("BESU_FAILURE_BINDING_CONFLICT");
            return r.getString(3);
        },e.executionId()).stream().findFirst();
    }
    void record(SettlementBinding.Execution e,BesuRevertEvidence.Proof proof) {
        jdbc.update("""
            insert into payment_besu_failure(execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,source_hash,tx_hash)
            values (?,?,?,cast(? as jsonb),?,?,?)
            """,e.executionId(),e.instruction().paymentId(),e.instruction().correlationId(),SettlementBindingJson.write(e),
            SettlementBindingJson.fingerprint(e),proof.sourceHash(),proof.sources().receipt().transactionHash());
    }
    String fundingActor(SettlementBinding.Execution e) {
        return jdbc.queryForObject("select actor_id from payment_besu_funding where execution_id=?",String.class,e.executionId());
    }
    Optional<String> authorized(SettlementBinding.Execution e) {
        return jdbc.query("select source_hash from payment_besu_recovery_authorization where execution_id=?",(r,n)->r.getString(1),e.executionId()).stream().findFirst();
    }
    void authorize(SettlementBinding.Execution e,String hash,String actor) {
        jdbc.update("""
            insert into payment_besu_recovery_authorization(execution_id,payment_id,correlation_id,execution_document,execution_fingerprint,source_hash,actor_id,action)
            values (?,?,?,cast(? as jsonb),?,?,?,?)
            """,e.executionId(),e.instruction().paymentId(),e.instruction().correlationId(),SettlementBindingJson.write(e),
            SettlementBindingJson.fingerprint(e),hash,actor,BesuRevertEvidence.ACTION);
    }
}
