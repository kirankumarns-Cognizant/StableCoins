package com.cognizant.stablecoins.flow;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Read-only preparation material; never quote, reservation or execution authority. */
public final class SettlementPreparation {
    private SettlementPreparation() {}
    public static final String EVENT = "SettlementObservationRequested";
    public record Request(UUID requestId, SettlementBinding.Instruction instruction, Instant requestedAt) {
        public Request {
            Objects.requireNonNull(requestId, "PREPARATION_ID_REQUIRED");
            Objects.requireNonNull(instruction, "INSTRUCTION_REQUIRED");
            if (instruction.context().token().chain().classification() != SettlementNetwork.Classification.PRIVATE_DEMO)
                throw new IllegalArgumentException("PRIVATE_PREPARATION_REQUIRED");
            if (requestedAt == null || !requestedAt.isBefore(instruction.deliverBy()))
                throw new IllegalArgumentException("INVALID_PREPARATION_TIME");
        }
        public void requireCurrent(Instant now) {
            if (now == null || now.isBefore(requestedAt) || !now.isBefore(requestedAt.plusSeconds(120))
                    || !now.isBefore(instruction.deliverBy()))
                throw new IllegalStateException("SETTLEMENT_PREPARATION_EXPIRED");
        }
    }
    public record Evidence(Request request, SettlementObservation observation) {
        public Evidence {
            Objects.requireNonNull(request, "PREPARATION_REQUIRED");
            Objects.requireNonNull(observation, "OBSERVATION_REQUIRED");
            if (!request.instruction().equals(observation.instruction())
                    || observation.observedAt().isBefore(request.requestedAt()))
                throw new IllegalArgumentException("SETTLEMENT_PREPARATION_BINDING_CHANGED");
            request.requireCurrent(observation.observedAt());
        }
        public void requireFor(Request expected, Instant now) {
            if (!request.equals(expected)) throw new IllegalStateException("SETTLEMENT_PREPARATION_BINDING_CHANGED");
            request.requireCurrent(now);
            observation.requireFor(expected.instruction(), now);
        }
    }
}
