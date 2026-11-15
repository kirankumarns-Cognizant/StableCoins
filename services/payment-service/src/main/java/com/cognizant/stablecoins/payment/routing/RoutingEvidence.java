package com.cognizant.stablecoins.payment.routing;

import com.cognizant.stablecoins.payment.domain.PaymentRail;
import com.cognizant.stablecoins.payment.intent.BeneficiaryOutcome;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Immutable, versioned evidence. Values are validated by the assessor, never defaulted to zero. */
public final class RoutingEvidence {
    private RoutingEvidence() { }
    public enum SignalType { SUCCESS_RATE, LATENCY, TRANSACTION_COST, FRAUD_RISK, RAIL_AVAILABILITY, LIQUIDITY, EXTERNAL_DATA }
    public enum EvidenceMode { SIMULATED, MOCK, LIVE }
    public enum Quality { VALID, UNKNOWN, INVALID, CONTRADICTORY }
    public enum Unit { RATIO, MILLISECONDS, USD, SCORE_0_100, STATUS, ASSET_AMOUNTS, TYPED_EXTERNAL }
    public enum Asset { USD, INR, USDC, ETH }
    public enum DeliveryScope { END_TO_END, CHAIN_ONLY }
    public enum Availability { AVAILABLE, DEGRADED, UNAVAILABLE, UNKNOWN }
    public enum PocketRole { TREASURY_USDC, GAS_ETH, PROVIDER_FIAT }
    public enum FeeComponent { FUNDING_CONVERSION, FX, GAS, OFF_RAMP, PAYOUT, PLATFORM, INFERENCE }
    public enum CostBasis { SIMULATED_DELIVERY_ESTIMATE }
    public enum ExternalKind { NETWORK_CONGESTION, STABLECOIN_PRICE_DEVIATION, PROVIDER_INCIDENT, CALENDAR_CUTOFF }
    public enum ExternalUnit { MILLISECONDS, BASIS_POINTS, FLAG }
    public enum ExternalState { NORMAL, BLOCKING, UNKNOWN }

    public record Network(String name, long chainId, String tokenContract, boolean testnet) { }
    public record Scope(UUID customerId, String corridor, BigDecimal sourceAmountUsd) { }
    public record Candidate(String candidateId, String providerId, PaymentRail rail,
            BeneficiaryOutcome outcome, Network network) { }
    public record Observation(String signalId, int schemaVersion, SignalType signalType, String candidateId,
            Scope scope, String sourceId, String sourceRecordReference, Instant observedAt, Instant receivedAt,
            Instant expiresAt, Unit unit, EvidenceMode evidenceMode, Quality quality, Network network, SignalValue value) { }
    public record Snapshot(UUID snapshotId, int schemaVersion, List<Candidate> candidates, List<Observation> observations) {
        public Snapshot { candidates = List.copyOf(candidates); observations = List.copyOf(observations); }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = SuccessRate.class, name = "SUCCESS_RATE"),
        @JsonSubTypes.Type(value = Latency.class, name = "LATENCY"),
        @JsonSubTypes.Type(value = TransactionCost.class, name = "TRANSACTION_COST"),
        @JsonSubTypes.Type(value = FraudRisk.class, name = "FRAUD_RISK"),
        @JsonSubTypes.Type(value = RailAvailability.class, name = "RAIL_AVAILABILITY"),
        @JsonSubTypes.Type(value = Liquidity.class, name = "LIQUIDITY"),
        @JsonSubTypes.Type(value = ExternalData.class, name = "EXTERNAL_DATA")
    })
    public sealed interface SignalValue permits SuccessRate, Latency, TransactionCost, FraudRisk,
            RailAvailability, Liquidity, ExternalData {
        SignalType type();
    }
    public record SuccessRate(long completed, long attempts, Duration window, DeliveryScope deliveryScope) implements SignalValue {
        public SignalType type() { return SignalType.SUCCESS_RATE; }
    }
    public record Latency(long estimatedDeliveryMillis, int percentile, long sampleSize,
            Duration window, DeliveryScope deliveryScope) implements SignalValue {
        public SignalType type() { return SignalType.LATENCY; }
    }
    /** Rate is a labeled budgeting assumption, never a measured market valuation of test tokens. */
    public record ConversionQuote(String quoteId, Asset from, BigDecimal usdPerUnit,
            Instant observedAt, Instant expiresAt, boolean simulatedBudgetRate) { }
    public record CostItem(FeeComponent component, BigDecimal nativeAmount, Asset nativeAsset,
            BigDecimal comparisonAmountUsd, ConversionQuote conversion) { }
    public record TransactionCost(List<CostItem> items, BigDecimal declaredTotalUsd, CostBasis basis) implements SignalValue {
        public TransactionCost { items = List.copyOf(items); }
        public SignalType type() { return SignalType.TRANSACTION_COST; }
    }
    public record FraudRisk(BigDecimal score, String modelVersion, String screeningReference) implements SignalValue {
        public SignalType type() { return SignalType.FRAUD_RISK; }
    }
    public record RailAvailability(Availability network, Availability rpc, Availability provider,
            Availability beneficiaryDelivery) implements SignalValue {
        public SignalType type() { return SignalType.RAIL_AVAILABILITY; }
    }
    public record Pocket(PocketRole role, Asset asset, BigDecimal total, BigDecimal reserved, BigDecimal available) { }
    public record Liquidity(List<Pocket> pockets) implements SignalValue {
        public Liquidity { pockets = List.copyOf(pockets); }
        public SignalType type() { return SignalType.LIQUIDITY; }
    }
    public record ExternalObservation(ExternalKind kind, ExternalUnit unit, BigDecimal magnitude,
            ExternalState state, String sourceId, String sourceRecordReference, Instant observedAt, Instant expiresAt) { }
    public record ExternalData(List<ExternalObservation> observations) implements SignalValue {
        public ExternalData { observations = List.copyOf(observations); }
        public SignalType type() { return SignalType.EXTERNAL_DATA; }
    }
}
