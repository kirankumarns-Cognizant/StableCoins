package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigInteger;
import java.util.*;

/** ADR-007 read-only owner projections; writes only the ledger-owned receipt acknowledgement. */
@Repository
@ConditionalOnProperty(name="app.besu.accounting.enabled",havingValue="true")
public class BesuAccountingStore {
    public record Submission(String hash,String status,BigInteger gasLimit,BigInteger gasPrice) {}
    public record Acknowledgement(BesuAccountingEvidence evidence,UUID confirmationJournal,UUID feeJournal) {}
    private final JdbcTemplate jdbc;
    public BesuAccountingStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void requireClaimed(SettlementBinding.Execution execution) { new SettlementExecutionReads(jdbc).requireClaimed(execution); }
    public void requireFunded(SettlementBinding.Execution e,String reference) {
        new SettlementFundingReads(jdbc,new com.fasterxml.jackson.databind.ObjectMapper(),
                new BesuClosureEvidence.Policy("adr015-v1","110002","110001","110003")).requireFunded(e,reference);
    }
    public Submission submission(SettlementBinding.Execution expected) {
        var rows=jdbc.query("select document::text,fingerprint,scope_key,tx_hash,status,gas_limit,gas_price from besu_signed_submission where execution_id=?",
            (rs,n)->{
                var e=SettlementBindingJson.read(rs.getString(1),SettlementBinding.Execution.class);
                if(!expected.equals(e) || !SettlementBindingJson.fingerprint(e).equals(rs.getString(2))
                        || !e.context().nonceScope().equals(rs.getString(3))) throw conflict();
                String status=rs.getString(5);
                if(!Set.of("ATTEMPTED","SUBMITTED","UNKNOWN","CONFIRMED","REVERTED").contains(status)) throw conflict();
                String hash=SettlementNetwork.hash(rs.getString(4));
                var limit=rs.getBigDecimal(6).toBigIntegerExact();var price=rs.getBigDecimal(7).toBigIntegerExact();
                if(limit.signum()<=0 || limit.compareTo(BigInteger.valueOf(30_000_000))>0 || price.signum()<0) throw conflict();
                return new Submission(hash,status,limit,price);
            },expected.executionId());
        if(rows.size()!=1) throw conflict();return rows.getFirst();
    }
    public BesuAccountingEvidence.Receipt receipt(SettlementBinding.Execution e,Submission submission) {
        var rows=jdbc.query("select tx_hash,block_hash,block_number,success,gas_used,gas_price,total_fee_wei,scope_key,finality_policy from besu_final_receipt where execution_id=?",
            (rs,n)->{
                var receipt=new BesuAccountingEvidence.Receipt(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getBoolean(4),
                        rs.getBigDecimal(5).toBigIntegerExact(),rs.getBigDecimal(6).toBigIntegerExact(),rs.getBigDecimal(7).toBigIntegerExact());
                if(!e.context().nonceScope().equals(rs.getString(8)) || !"QBFT_COMMITTED".equals(rs.getString(9))
                        || !receipt.transactionHash().equals(submission.hash())
                        || receipt.gasUsed().compareTo(submission.gasLimit())>0 || !receipt.gasPriceWei().equals(submission.gasPrice())
                        || !(receipt.success()?"CONFIRMED":"REVERTED").equals(submission.status())) throw conflict();
                return receipt;
            },e.executionId());
        if(rows.size()!=1) throw conflict();return rows.getFirst();
    }
    public Optional<Acknowledgement> acknowledgement(UUID executionId) {
        var rows=jdbc.query("select evidence::text,confirmation_journal,fee_journal from besu_ledger_receipt where execution_id=?",
            (rs,n)->new Acknowledgement(BesuAccountingEvidence.read(rs.getString(1)),
                    rs.getObject(2,UUID.class),rs.getObject(3,UUID.class)),executionId);
        if(rows.size()>1) throw conflict();return rows.stream().findFirst();
    }
    public void acknowledge(SettlementBinding.Execution e,Acknowledgement acknowledgement) {
        jdbc.update("insert into besu_ledger_receipt(execution_id,payment_id,correlation_id,evidence,confirmation_journal,fee_journal) values (?,?,?,cast(? as jsonb),?,?)",
                e.executionId(),e.instruction().paymentId(),e.instruction().correlationId(),acknowledgement.evidence().json(),
                acknowledgement.confirmationJournal(),acknowledgement.feeJournal());
    }
    private static IllegalStateException conflict() { return new IllegalStateException("BESU_LEDGER_OWNER_EVIDENCE_REQUIRED"); }
}
