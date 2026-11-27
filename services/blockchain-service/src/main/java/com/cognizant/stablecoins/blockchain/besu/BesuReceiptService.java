package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.math.BigInteger;
import java.time.Clock;
import java.util.*;

/** Internal receipt owner. No scheduler/API, financial postings or payment-owner writes. */
@Service
@ConditionalOnProperty(name="app.besu.enabled",havingValue="true")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class BesuReceiptService {
    private final BesuRuntime runtime;
    private final SettlementExecutionReads claims;
    private final BesuLiquidityStore liquidity;
    private final BesuSubmissionStore submissions;
    private final TransactionOperations database;
    @Autowired
    public BesuReceiptService(JdbcTemplate jdbc,BesuRuntime runtime,PlatformTransactionManager manager) {
        this(runtime,new SettlementExecutionReads(jdbc),new BesuLiquidityStore(jdbc,Clock.systemUTC()),
                new BesuSubmissionStore(jdbc),transactions(manager));
    }
    BesuReceiptService(BesuRuntime runtime,SettlementExecutionReads claims,BesuLiquidityStore liquidity,
                       BesuSubmissionStore submissions,TransactionOperations database) {
        this.runtime=runtime; this.claims=claims; this.liquidity=liquidity;
        this.submissions=submissions; this.database=database;
    }
    private static TransactionTemplate transactions(PlatformTransactionManager manager) {
        var tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }
    public Result confirm(SettlementBinding.Execution e) {
        Objects.requireNonNull(e,"EXECUTION_REQUIRED");
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("BESU_RECEIPT_OUTSIDE_TRANSACTION_REQUIRED");
        var saved=database.execute(s->locked(e));
        if (terminal(saved.status())) return replay(saved);
        if ("SIGNED".equals(saved.status())) return result(saved,Optional.empty(),"NOT_ATTEMPTED");
        Optional<BesuFinalReceipt> receipt;
        try { receipt=runtime.forPinnedExecution(e.context()).finalReceipt(saved.transfer()); }
        catch (RuntimeException ignored) {
            // Missing/malformed/mismatched evidence never releases capacity or exposes provider errors.
            return result(saved,Optional.empty(),"RECEIPT_UNVERIFIED");
        }
        if (receipt.isEmpty()) return result(saved,receipt,"RECEIPT_PENDING");
        receipt.get().requireFor(saved.transfer());
        return database.execute(s->{
            var current=locked(e);
            if (terminal(current.status())) return replay(current);
            submissions.resolve(current,receipt.get());
            return result(new BesuSubmissionStore.Stored(current.transfer(),receipt.get().status()),receipt,"QBFT_COMMITTED");
        });
    }
    private BesuSubmissionStore.Stored locked(SettlementBinding.Execution e) {
        claims.requireClaimed(e); liquidity.lock(e.context()); claims.requireClaimed(e);
        if (liquidity.pinnedObservation(e).isEmpty()) throw new IllegalStateException("BESU_PINNED_RESERVATION_REQUIRED");
        return submissions.find(e).orElseThrow(()->new IllegalStateException("BESU_SIGNED_SUBMISSION_REQUIRED"));
    }
    private Result replay(BesuSubmissionStore.Stored saved) {
        var receipt=submissions.receipt(saved.transfer()).orElseThrow(()->new IllegalStateException("BESU_FINAL_RECEIPT_REQUIRED"));
        if (!receipt.status().equals(saved.status())) throw new IllegalStateException("BESU_FINAL_RECEIPT_BINDING_CONFLICT");
        return result(saved,Optional.of(receipt),"QBFT_COMMITTED");
    }
    private static boolean terminal(String status) { return Set.of("CONFIRMED","REVERTED").contains(status); }
    private static Result result(BesuSubmissionStore.Stored saved,Optional<BesuFinalReceipt> receipt,String evidence) {
        return new Result(saved.transfer().execution.executionId(),saved.transfer().hash,saved.status(),evidence,
                receipt.map(BesuFinalReceipt::blockNumber).orElse(null),receipt.map(BesuFinalReceipt::totalFeeWei).orElse(null));
    }
    public record Result(UUID executionId,String transactionHash,String status,String receiptEvidence,Long blockNumber,BigInteger feeWei) {
        public String executionGate() { return "PAYMENT_ACCOUNTING_INTEGRATION_PENDING"; }
    }
}
