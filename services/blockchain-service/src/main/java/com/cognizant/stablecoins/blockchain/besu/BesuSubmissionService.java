package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;

/** Internal B03 owner. No endpoint/listener or automatic broadcast retries. */
@Service
@ConditionalOnProperty(name="app.besu.enabled",havingValue="true")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class BesuSubmissionService {
    private final BesuRuntime runtime;
    private final SettlementExecutionReads claims;
    private final SettlementPreparationReads owners;
    private final SettlementFundingReads funding;
    private final BesuLiquidityStore liquidity;
    private final BesuSubmissionStore submissions;
    private final TransactionOperations database;
    private final Clock clock;

    @Autowired
    public BesuSubmissionService(JdbcTemplate jdbc,ObjectMapper mapper,BesuRuntime runtime,
                                 PlatformTransactionManager manager,org.springframework.core.env.Environment env) {
        this(runtime,new SettlementExecutionReads(jdbc),new SettlementPreparationReads(jdbc,mapper),
                new SettlementFundingReads(jdbc,mapper,new BesuClosureEvidence.Policy(
                    env.getProperty("app.ledger.accounting.version","adr015-v1"),
                    env.getProperty("app.ledger.accounting.accounts.funding-usd","110002"),
                    env.getProperty("app.ledger.accounting.accounts.corporate-usd","110001"),
                    env.getProperty("app.ledger.accounting.accounts.conversion-usd","110003"))),
                new BesuLiquidityStore(jdbc,Clock.systemUTC()),new BesuSubmissionStore(jdbc),transactions(manager),Clock.systemUTC());
    }
    BesuSubmissionService(BesuRuntime runtime,SettlementExecutionReads claims,SettlementPreparationReads owners,
            SettlementFundingReads funding,BesuLiquidityStore liquidity,BesuSubmissionStore submissions,TransactionOperations database,Clock clock) {
        this.runtime=runtime; this.claims=claims; this.owners=owners; this.funding=funding; this.liquidity=liquidity;
        this.submissions=submissions; this.database=database; this.clock=clock;
    }
    private static TransactionTemplate transactions(PlatformTransactionManager manager) {
        var tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }

    public Result submit(SettlementBinding.Execution e) {
        Objects.requireNonNull(e,"EXECUTION_REQUIRED");
        // Also fail closed if invoked directly without the Spring NOT_SUPPORTED proxy.
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("BESU_SUBMISSION_OUTSIDE_TRANSACTION_REQUIRED");
        var stored=database.execute(s->prepare(e)); // Signed bytes/nonce/hash commit first.
        if (!"SIGNED".equals(stored.status())) return recover(e,stored);
        boolean attempt=Boolean.TRUE.equals(database.execute(s->{
            lock(e);
            var current=required(e);
            if (!"SIGNED".equals(current.status())) return false;
            var fresh=fresh(e);
            // Never replace/reprice an existing signature. Changed fees require investigation/new review.
            if (current.transfer().gasLimit.compareTo(fresh.gasLimit())<0
                    || current.transfer().gasPrice.compareTo(fresh.gasPriceWei())<0)
                throw new IllegalStateException("BESU_SIGNED_FEE_REVIEW_REQUIRED");
            submissions.transition(e,"SIGNED","ATTEMPTED");
            return true;
        })); // Attempt marker commits BEFORE any broadcast, including uncertain RPC outcomes.
        if (!attempt) return recover(e,database.execute(s->{lock(e);return required(e);}));
        var adapter=runtime.forPinnedExecution(e.context());
        boolean accepted=false;
        try { accepted=stored.transfer().hash.equalsIgnoreCase(adapter.submitRawTransaction(stored.transfer().payload())); }
        catch (RuntimeException ignored) { /* Provider errors may contain secrets. Retain exact hash and funds. */ }
        return finish(e,accepted);
    }

    /** Restart monitoring never signs or marks a first attempt, even for a saved SIGNED row. */
    public Result observeSubmission(SettlementBinding.Execution e) {
        Objects.requireNonNull(e,"EXECUTION_REQUIRED");
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("BESU_SUBMISSION_OUTSIDE_TRANSACTION_REQUIRED");
        var saved=database.execute(s->{lock(e);return required(e);});
        if ("SIGNED".equals(saved.status()))
            return new Result(e.executionId(),saved.transfer().hash,saved.transfer().nonce,"SIGNED");
        return recover(e,saved);
    }

    private BesuSubmissionStore.Stored prepare(SettlementBinding.Execution e) {
        lock(e);
        var existing=submissions.find(e);
        if (existing.isPresent()) return existing.get(); // Historical recovery need not renew expired approvals.
        submissions.requireNoOtherSubmission(e);
        var fresh=fresh(e);
        var adapter=runtime.forPinnedExecution(e.context());
        long nonce=adapter.getPendingNonce(e.context().treasury());
        if (nonce<0 || nonce==Long.MAX_VALUE) throw new IllegalStateException("BESU_NONCE_INVALID");
        fresh.requireFor(e.instruction(),clock.instant()); e.review().requireFresh(clock.instant());
        owners.requireFundedInstruction(e.instruction()); funding.requireAllocated(e); claims.requireClaimed(e);
        var bytes=adapter.signRawTransaction(nonce,e.instruction().recipient(),e.context().token().contract(),
                e.instruction().tokenAmount(),e.context().token().decimals(),fresh.gasLimit(),fresh.gasPriceWei());
        var signed=new BesuSignedTransfer(e,nonce,fresh.gasLimit(),fresh.gasPriceWei(),bytes);
        // Signing/RPC may take time. Expiry or owner changes roll back nonce/payload ownership.
        fresh.requireFor(e.instruction(),clock.instant());
        e.review().requireFresh(clock.instant());
        owners.requireFundedInstruction(e.instruction()); funding.requireAllocated(e); claims.requireClaimed(e);
        submissions.save(signed);
        return new BesuSubmissionStore.Stored(signed,"SIGNED");
    }
    private SettlementObservation fresh(SettlementBinding.Execution e) {
        var began=clock.instant();
        e.review().requireFresh(began); owners.requireFundedInstruction(e.instruction()); funding.requireAllocated(e);
        var fresh=runtime.observe(e.instruction());
        if (fresh.observedAt().isBefore(began))
            throw new IllegalStateException("BESU_EXECUTION_OBSERVATION_PREDATES_CHECK");
        liquidity.requireSigningCapacity(e,fresh);
        owners.requireFundedInstruction(e.instruction()); funding.requireAllocated(e); claims.requireClaimed(e);
        fresh.requireFor(e.instruction(),clock.instant()); e.review().requireFresh(clock.instant());
        return fresh;
    }
    private Result recover(SettlementBinding.Execution e,BesuSubmissionStore.Stored stored) {
        if (Set.of("CONFIRMED","REVERTED").contains(stored.status())) {
            var receipt=submissions.receipt(stored.transfer()).orElseThrow(()->new IllegalStateException("BESU_FINAL_RECEIPT_REQUIRED"));
            if (!receipt.status().equals(stored.status())) throw new IllegalStateException("BESU_FINAL_RECEIPT_BINDING_CONFLICT");
            return new Result(e.executionId(),stored.transfer().hash,stored.transfer().nonce,stored.status());
        }
        // Observation only: no re-sign, nonce replacement, broadcast or chain switch on redelivery.
        boolean observed=false;
        try { observed=runtime.forPinnedExecution(e.context()).observesTransaction(stored.transfer().payload()); }
        catch (RuntimeException ignored) { /* Read failure is uncertainty, not evidence of absence/failure. */ }
        return finish(e,observed);
    }
    private Result finish(SettlementBinding.Execution e,boolean accepted) {
        return database.execute(s->{
            lock(e); var current=required(e); String state=current.status();
            if (accepted && Set.of("ATTEMPTED","UNKNOWN").contains(state)) {
                submissions.transition(e,state,"SUBMITTED"); state="SUBMITTED";
            } else if (!accepted && "ATTEMPTED".equals(state)) {
                submissions.transition(e,state,"UNKNOWN"); state="UNKNOWN";
            }
            return new Result(e.executionId(),current.transfer().hash,current.transfer().nonce,state);
        });
    }
    private void lock(SettlementBinding.Execution e) {
        claims.requireClaimed(e); liquidity.lock(e.context()); claims.requireClaimed(e);
        if (liquidity.pinnedObservation(e).isEmpty())
            throw new IllegalStateException("BESU_PINNED_RESERVATION_REQUIRED");
    }
    private BesuSubmissionStore.Stored required(SettlementBinding.Execution e) {
        return submissions.find(e).orElseThrow(()->new IllegalStateException("BESU_SIGNED_SUBMISSION_REQUIRED"));
    }
    public record Result(UUID executionId,String transactionHash,long nonce,String status) {
        public String executionGate() { return "RECEIPT_WORKFLOW_INTEGRATION_PENDING"; }
    }
}