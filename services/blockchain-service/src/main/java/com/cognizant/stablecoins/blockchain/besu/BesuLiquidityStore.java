package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.*;

/**
 * Blockchain-owner storage, deliberately package-private and without an API/event
 * handler. hold/pin require a payment-owner claim. Canonical material alone is
 * not approval authority: the reservation worker verifies the exact owner claim,
 * two approvals and quote before invoking these methods in its transaction.
 * PINNED history stays immutable; a final receipt atomically resolves its capacity commitment.
 */
final class BesuLiquidityStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SettlementExecutionReads claims;
    BesuLiquidityStore(JdbcTemplate jdbc, Clock clock) { this(jdbc,clock,new SettlementExecutionReads(jdbc)); }
    BesuLiquidityStore(JdbcTemplate jdbc, Clock clock, SettlementExecutionReads claims) {
        this.jdbc=Objects.requireNonNull(jdbc); this.clock=Objects.requireNonNull(clock);
        this.claims=Objects.requireNonNull(claims);
    }

    void lock(SettlementNetwork.Context context) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("BESU_LIQUIDITY_TRANSACTION_REQUIRED");
        if (!Integer.valueOf(java.sql.Connection.TRANSACTION_READ_COMMITTED).equals(
                TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new IllegalStateException("BESU_LIQUIDITY_READ_COMMITTED_REQUIRED");
        if (context.token().chain().classification() != SettlementNetwork.Classification.PRIVATE_DEMO)
            throw new IllegalArgumentException("PRIVATE_LIQUIDITY_REQUIRED");
        jdbc.update("insert into besu_treasury_scope(scope_key) values (?) on conflict do nothing", context.nonceScope());
        var scopes = jdbc.queryForList("select scope_key from besu_treasury_scope where scope_key=? for update",
                String.class, context.nonceScope());
        if (scopes.size() != 1) throw new IllegalStateException("BESU_TREASURY_LOCK_REQUIRED");
    }

    SettlementAvailability snapshot(SettlementPreparation.Evidence evidence) {
        var context = evidence.request().instruction().context();
        lock(context);
        Instant now=clock.instant(); // Read time after any lock wait.
        evidence.requireFor(evidence.request(), now);
        var total = totals(context, now, evidence.observation());
        return new SettlementAvailability(evidence, total[0], total[1], now);
    }

    /** Exact replay preserves the original terms/lease, including an expired lease; it does not grant a new hold. */
    void hold(SettlementBinding.Execution execution, SettlementObservation observation) {
        lock(execution.context());
        Instant now=clock.instant();
        requireCanonical(execution);
        var stored = existing(execution.executionId());
        if (stored.isPresent()) {
            if (!stored.get().execution().equals(execution)) throw conflict();
            return;
        }
        execution.review().requireFresh(now);
        observation.requireFor(execution.instruction(), now);
        if (execution.review().nativeFeeReserve().compareTo(observation.executionFeeEstimate()) < 0)
            throw new IllegalStateException("BESU_APPROVED_GAS_RESERVE_TOO_SMALL");
        var total = totals(execution.context(), now, observation);
        if (observation.tokenTotal().subtract(total[0]).compareTo(execution.instruction().tokenAmount()) < 0
                || observation.nativeTotal().subtract(total[1]).compareTo(execution.review().nativeFeeReserve()) < 0)
            throw new IllegalStateException("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT");
        jdbc.update("insert into besu_liquidity_commitment(execution_id,payment_id,scope_key,token_contract,document,fingerprint,status,expires_at) values (?,?,?,?,cast(? as jsonb),?,'HELD',?)",
                execution.executionId(),execution.instruction().paymentId(),execution.context().nonceScope(),
                execution.context().token().contract(),SettlementBindingJson.write(execution),
                SettlementBindingJson.fingerprint(execution),Timestamp.from(execution.review().expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }

    /** Must commit before any signing/submission; no nonce/signature is acquired here. */
    void pin(SettlementBinding.Execution execution) {
        lock(execution.context());
        Instant now=clock.instant();
        requireCanonical(execution);
        var row = existing(execution.executionId()).orElseThrow(BesuLiquidityStore::conflict);
        if (!row.execution().equals(execution)) throw conflict();
        if ("PINNED".equals(row.status())) return;
        execution.review().requireFresh(now);
        if (!"HELD".equals(row.status())) throw conflict();
        jdbc.update("update besu_liquidity_commitment set status='PINNED',pinned_at=? where execution_id=? and status='HELD'",
                Timestamp.from(now),execution.executionId());
    }

    void releaseUnsigned(SettlementBinding.Execution execution) {
        lock(execution.context());
        var row = existing(execution.executionId()).orElseThrow(BesuLiquidityStore::conflict);
        if (!row.execution().equals(execution)) throw conflict();
        if ("RELEASED".equals(row.status())) return;
        if (!"HELD".equals(row.status())) throw new IllegalStateException("BESU_PINNED_COMMITMENT_REQUIRES_RECOVERY");
        jdbc.update("update besu_liquidity_commitment set status='RELEASED' where execution_id=? and status='HELD'",
                execution.executionId());
    }

    /** Revalidate all active commitments, including this execution's already-pinned hold. */
    void requireSigningCapacity(SettlementBinding.Execution e, SettlementObservation fresh) {
        lock(e.context());
        requireCanonical(e);
        if (pinnedObservation(e).isEmpty()) throw new IllegalStateException("BESU_PINNED_RESERVATION_REQUIRED");
        e.review().requireFresh(clock.instant());
        fresh.requireFor(e.instruction(),clock.instant());
        if (fresh.executionFeeEstimate().compareTo(e.review().nativeFeeReserve())>0)
            throw new IllegalStateException("BESU_APPROVED_GAS_RESERVE_TOO_SMALL");
        var total=totals(e.context(),clock.instant(),fresh);
        if (fresh.tokenTotal().compareTo(total[0])<0 || fresh.nativeTotal().compareTo(total[1])<0)
            throw new IllegalStateException("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT");
    }

    private BigDecimal[] totals(SettlementNetwork.Context context, Instant now, SettlementObservation observation) {
        // Never remove a settled hold from a balance captured before its on-chain debit.
        Long resolvedBlock=jdbc.queryForObject("select max(block_number) from besu_final_receipt where scope_key=?",
                Long.class,context.nonceScope());
        if (resolvedBlock!=null && observation.observedBlock()<resolvedBlock)
            throw new IllegalStateException("BESU_BALANCE_PREDATES_RESOLUTION");
        BigDecimal token = BigDecimal.ZERO, nativeGas = BigDecimal.ZERO;
        var rows = rows("select execution_id,payment_id,scope_key,token_contract,document::text,fingerprint,status,expires_at from besu_liquidity_commitment where scope_key=? and status<>'RELEASED' and not exists (select 1 from besu_final_receipt r where r.execution_id=besu_liquidity_commitment.execution_id) order by execution_id limit 1001",
                context.nonceScope());
        if (rows.size() > 1000) throw new IllegalStateException("BESU_COMMITMENT_CAPACITY_EXCEEDED");
        for (var row : rows) {
            var e = row.execution();
            if (!e.context().nonceScope().equals(context.nonceScope())) throw conflict();
            // PINNED survives every readiness/quote expiry, including unknown submission.
            if ("PINNED".equals(row.status()) || ("HELD".equals(row.status()) && now.isBefore(row.expiresAt()))) {
                nativeGas = nativeGas.add(e.review().nativeFeeReserve());
                if (e.context().token().contract().equals(context.token().contract())) {
                    if (!e.context().token().equals(context.token())) throw conflict();
                    token = token.add(e.instruction().tokenAmount());
                }
            }
        }
        return new BigDecimal[]{token,nativeGas};
    }

    /** Historical result only. A partial/released hold must never become new reservation authority. */
    Optional<SettlementObservation> pinnedObservation(SettlementBinding.Execution execution) {
        var commitment=existing(execution.executionId());
        var observations=jdbc.query("select document::text,fingerprint from besu_execution_reservation where execution_id=?",
                (rs,n)->{
                    var o=SettlementBindingJson.read(rs.getString(1),SettlementObservation.class);
                    if (!o.instruction().equals(execution.instruction())
                            || !SettlementBindingJson.fingerprint(o).equals(rs.getString(2))
                            || o.observedAt().isBefore(execution.review().assessedAt())
                            || !o.observedAt().isBefore(execution.review().expiresAt())
                            || execution.review().nativeFeeReserve().compareTo(o.executionFeeEstimate())<0)
                        throw conflict();
                    return o;
                },execution.executionId());
        if (commitment.isEmpty() && observations.isEmpty()) return Optional.empty();
        if (commitment.isEmpty() || observations.size()!=1
                || !commitment.get().execution().equals(execution)
                || !"PINNED".equals(commitment.get().status()))
            throw new IllegalStateException("BESU_RESERVATION_REQUIRES_RECOVERY");
        return Optional.of(observations.getFirst());
    }

    void capturePinnedObservation(SettlementBinding.Execution execution, SettlementObservation observation) {
        lock(execution.context());
        requireCanonical(execution);
        execution.review().requireFresh(clock.instant());
        observation.requireFor(execution.instruction(),clock.instant());
        var row=existing(execution.executionId()).orElseThrow(BesuLiquidityStore::conflict);
        if (!row.execution().equals(execution) || !"PINNED".equals(row.status())) throw conflict();
        jdbc.update("insert into besu_execution_reservation(execution_id,document,fingerprint) values (?,cast(? as jsonb),?)",
                execution.executionId(),SettlementBindingJson.write(observation),SettlementBindingJson.fingerprint(observation));
        if (pinnedObservation(execution).filter(observation::equals).isEmpty()) throw conflict();
    }

    private void requireCanonical(SettlementBinding.Execution execution) {
        claims.requireClaimed(execution);
    }
    private Optional<Row> existing(UUID id) {
        var found=rows("select execution_id,payment_id,scope_key,token_contract,document::text,fingerprint,status,expires_at from besu_liquidity_commitment where execution_id=?",id);
        if(found.size()>1)throw conflict();
        return found.stream().findFirst();
    }
    private record Row(SettlementBinding.Execution execution,String status,Instant expiresAt) {}
    private List<Row> rows(String sql,Object arg) {
        return jdbc.query(sql,(rs,n)->{
            var e=SettlementBindingJson.read(rs.getString(5),SettlementBinding.Execution.class);
            String status=rs.getString(7);
            Instant expires=rs.getTimestamp(8).toInstant();
            if (!e.executionId().equals(rs.getObject(1,UUID.class))
                    || !e.instruction().paymentId().equals(rs.getString(2))
                    || !e.context().nonceScope().equals(rs.getString(3))
                    || !e.context().token().contract().equals(rs.getString(4))
                    || !SettlementBindingJson.fingerprint(e).equals(rs.getString(6))
                    || !Set.of("HELD","PINNED","RELEASED").contains(status)
                    || !e.review().expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS).equals(expires)) throw conflict();
            return new Row(e,status,e.review().expiresAt());
        },arg);
    }
    private static IllegalStateException conflict() {
        return new IllegalStateException("BESU_COMMITMENT_BINDING_CONFLICT");
    }
}
