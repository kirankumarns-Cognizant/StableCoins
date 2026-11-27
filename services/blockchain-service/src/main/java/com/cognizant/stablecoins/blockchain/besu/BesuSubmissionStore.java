package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Caller holds BesuLiquidityStore's READ_COMMITTED treasury lock in every transaction. */
final class BesuSubmissionStore {
    record Stored(BesuSignedTransfer transfer,String status) {}
    private final JdbcTemplate jdbc;
    BesuSubmissionStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    Optional<Stored> find(SettlementBinding.Execution expected) {
        var rows=jdbc.query("select document::text,fingerprint,scope_key,nonce,gas_limit,gas_price,signed_payload,tx_hash,status from besu_signed_submission where execution_id=?",
                (rs,n)->{
                    var e=SettlementBindingJson.read(rs.getString(1),SettlementBinding.Execution.class);
                    if (!e.equals(expected) || !SettlementBindingJson.fingerprint(e).equals(rs.getString(2))
                            || !e.context().nonceScope().equals(rs.getString(3))) throw conflict();
                    var signed=new BesuSignedTransfer(e,rs.getLong(4),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getBytes(7));
                    if (!signed.hash.equals(rs.getString(8))
                            || !Set.of("SIGNED","ATTEMPTED","SUBMITTED","UNKNOWN","CONFIRMED","REVERTED").contains(rs.getString(9))) throw conflict();
                    return new Stored(signed,rs.getString(9));
                },expected.executionId());
        if (rows.size()>1) throw conflict();
        return rows.stream().findFirst();
    }
    void requireNoOtherSubmission(SettlementBinding.Execution e) {
        // Only conclusive, atomically recorded receipts resolve the serial treasury fence.
        Integer count=jdbc.queryForObject("select count(*) from besu_signed_submission where scope_key=? and execution_id<>? and status not in ('CONFIRMED','REVERTED')",
                Integer.class,e.context().nonceScope(),e.executionId());
        if (count==null || count!=0) throw new IllegalStateException("BESU_TREASURY_SUBMISSION_UNRESOLVED");
    }
    Optional<BesuFinalReceipt> receipt(BesuSignedTransfer t) {
        var rows=jdbc.query("select tx_hash,block_hash,block_number,success,gas_used,gas_price,total_fee_wei from besu_final_receipt where execution_id=? and scope_key=?",
                (rs,n)->new BesuFinalReceipt(t.execution.executionId(),rs.getString(1),rs.getString(2),rs.getLong(3),
                        rs.getBoolean(4),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getBigDecimal(7).toBigIntegerExact()),
                t.execution.executionId(),t.execution.context().nonceScope());
        if (rows.size()>1) throw conflict();
        rows.forEach(r->r.requireFor(t));
        return rows.stream().findFirst();
    }
    void resolve(Stored stored,BesuFinalReceipt receipt) {
        var t=stored.transfer(); receipt.requireFor(t);
        if (!Set.of("ATTEMPTED","UNKNOWN","SUBMITTED").contains(stored.status())) throw conflict();
        jdbc.update("insert into besu_final_receipt(execution_id,scope_key,tx_hash,block_hash,block_number,success,gas_used,gas_price,total_fee_wei) values (?,?,?,?,?,?,?,?,?)",
                t.execution.executionId(),t.execution.context().nonceScope(),receipt.transactionHash(),receipt.blockHash(),
                receipt.blockNumber(),receipt.success(),receipt.gasUsed(),receipt.gasPriceWei(),new java.math.BigDecimal(receipt.totalFeeWei()));
        transition(t.execution,stored.status(),receipt.status());
    }
    void save(BesuSignedTransfer t) {
        jdbc.update("insert into besu_signed_submission(execution_id,scope_key,document,fingerprint,nonce,gas_limit,gas_price,signed_payload,tx_hash,status) values (?,?,cast(? as jsonb),?,?,?,?,?,?,'SIGNED')",
                t.execution.executionId(),t.execution.context().nonceScope(),SettlementBindingJson.write(t.execution),
                SettlementBindingJson.fingerprint(t.execution),t.nonce,t.gasLimit,t.gasPrice,t.payload(),t.hash);
        audit(t.execution,"SIGNED");
    }
    void transition(SettlementBinding.Execution e,String before,String after) {
        if (jdbc.update("update besu_signed_submission set status=? where execution_id=? and status=?",
                after,e.executionId(),before)!=1) throw conflict();
        audit(e,after);
    }
    private void audit(SettlementBinding.Execution e,String status) {
        jdbc.update("insert into besu_submission_audit(audit_id,execution_id,status) values (?,?,?)",
                UUID.randomUUID(),e.executionId(),status);
    }
    private static IllegalStateException conflict() { return new IllegalStateException("BESU_SUBMISSION_BINDING_CONFLICT"); }
}