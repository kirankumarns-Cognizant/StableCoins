package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.Objects;

/**
 * Internal B02 boundary. No HTTP, queue listener, scheduler, signature or submission.
 * A future worker supplies exact owner terms; caller-supplied terms alone cannot reserve.
 */
@Service
@ConditionalOnProperty(name="app.besu.enabled",havingValue="true")
public class BesuExecutionReservationService {
    private final BesuRuntime runtime;
    private final SettlementExecutionReads claims;
    private final SettlementPreparationReads owners;
    private final BesuLiquidityStore liquidity;
    private final Clock clock;

    @Autowired
    public BesuExecutionReservationService(JdbcTemplate jdbc,ObjectMapper mapper,BesuRuntime runtime) {
        this(runtime,new SettlementExecutionReads(jdbc),new SettlementPreparationReads(jdbc,mapper),
                new BesuLiquidityStore(jdbc,Clock.systemUTC()),Clock.systemUTC());
    }
    BesuExecutionReservationService(BesuRuntime runtime,SettlementExecutionReads claims,
            SettlementPreparationReads owners,BesuLiquidityStore liquidity,Clock clock) {
        this.runtime=runtime; this.claims=claims; this.owners=owners; this.liquidity=liquidity; this.clock=clock;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Reservation reserve(SettlementBinding.Execution execution) {
        Objects.requireNonNull(execution,"EXECUTION_REQUIRED");
        claims.requireClaimed(execution);
        liquidity.lock(execution.context());
        claims.requireClaimed(execution); // Re-read after any treasury lock wait.
        var prior=liquidity.pinnedObservation(execution);
        if (prior.isPresent()) return new Reservation(execution,prior.get());
        var began=clock.instant();
        execution.review().requireFresh(began);
        owners.requireInstruction(execution.instruction());
        // Runtime checks exact configured network/genesis/token identity and current RPC readiness.
        var fresh=runtime.observe(execution.instruction());
        if (fresh.observedAt().isBefore(began))
            throw new IllegalStateException("BESU_EXECUTION_OBSERVATION_PREDATES_CHECK");
        fresh.requireFor(execution.instruction(),clock.instant());
        owners.requireInstruction(execution.instruction()); // Fail closed on owner changes during RPC.
        claims.requireClaimed(execution);
        liquidity.hold(execution,fresh);
        liquidity.pin(execution);
        liquidity.capturePinnedObservation(execution,fresh);
        return new Reservation(execution,fresh);
    }

    /** Recorded capacity only. Even a PINNED result cannot authorize signing or broadcast. */
    public record Reservation(SettlementBinding.Execution execution,SettlementObservation observation) {
        public Reservation {
            Objects.requireNonNull(execution); Objects.requireNonNull(observation);
            if (!execution.instruction().equals(observation.instruction()))
                throw new IllegalArgumentException("BESU_RESERVATION_BINDING_CHANGED");
        }
        public String status() { return "PINNED_NOT_SUBMITTED"; }
        public String executionGate() { return "DURABLE_SUBMISSION_INTEGRATION_PENDING"; }
    }
}