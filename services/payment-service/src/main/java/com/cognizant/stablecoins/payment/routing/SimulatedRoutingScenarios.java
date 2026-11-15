package com.cognizant.stablecoins.payment.routing;

import com.cognizant.stablecoins.payment.domain.PaymentRail;
import com.cognizant.stablecoins.payment.intent.BeneficiaryOutcome;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.routing.RoutingEvidence.*;

/** Explicit controlled fixtures, never a live-data fallback. Not registered as a production feed. */
public final class SimulatedRoutingScenarios {
    private SimulatedRoutingScenarios() { }
    public enum Scenario { NORMAL, CONGESTION, PROVIDER_OUTAGE, LOW_USDC, LOW_ETH, LOW_FIAT,
        FRAUD_BREACH, STALE_EVIDENCE, CONTRADICTORY_LIQUIDITY, NO_ELIGIBLE_ROUTE }

    public static Snapshot create(Scenario scenario, AssessmentContext context, BeneficiaryOutcome outcome,
            Network network, Instant now) {
        Objects.requireNonNull(scenario); Objects.requireNonNull(context); Objects.requireNonNull(outcome);
        Objects.requireNonNull(network); Objects.requireNonNull(now);
        String key = context.paymentId() + "/" + context.correlationId() + "/" + context.customerId() + "/" + context.corridor()
                + "/" + context.sourceAmountUsd().stripTrailingZeros().toPlainString() + "/" + context.transferAmountUsdc().stripTrailingZeros().toPlainString()
                + "/" + context.requiredGasEth().stripTrailingZeros().toPlainString() + "/" + new TreeMap<>(context.requiredPayoutAmounts())
                + "/" + network + "/" + outcome + "/" + scenario + "/" + now;
        UUID snapshotId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        List<Candidate> candidates = new ArrayList<>();
        List<Observation> observations = new ArrayList<>();
        for (String variant : List.of("A", "B")) {
            boolean first = variant.equals("A");
            String candidateId = "SIMULATED-" + variant;
            Candidate candidate = new Candidate(candidateId, "SIMULATED-PROVIDER-" + variant,
                    outcome == BeneficiaryOutcome.USDC_WALLET ? PaymentRail.STABLECOIN : PaymentRail.STABLECOIN_PLUS_VISA, outcome, network);
            candidates.add(candidate);
            Instant observed = first && scenario == Scenario.STALE_EVIDENCE ? now.minusSeconds(600) : now.minusSeconds(5);
            List<CostItem> fees = new ArrayList<>();
            for (FeeComponent component : FeeComponent.values()) {
                BigDecimal amount = switch (component) {
                    case FUNDING_CONVERSION -> new BigDecimal("0.50");
                    case FX -> outcome == BeneficiaryOutcome.USDC_WALLET ? BigDecimal.ZERO : new BigDecimal("0.50");
                    case GAS -> new BigDecimal("0.20");
                    case OFF_RAMP, PAYOUT -> outcome == BeneficiaryOutcome.USDC_WALLET ? BigDecimal.ZERO : BigDecimal.ONE;
                    case PLATFORM -> new BigDecimal(first ? "2.00" : "5.00");
                    case INFERENCE -> new BigDecimal("0.10");
                };
                fees.add(component == FeeComponent.GAS
                        ? new CostItem(component, new BigDecimal("0.0001"), Asset.ETH, amount,
                            new ConversionQuote("SIMULATED-BUDGET-ETH-USD", Asset.ETH, new BigDecimal("2000"), observed, observed.plusSeconds(120), true))
                        : new CostItem(component, amount, Asset.USD, amount, null));
            }
            BigDecimal total = fees.stream().map(CostItem::comparisonAmountUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Pocket> pockets = new ArrayList<>();
            pockets.add(pocket(PocketRole.TREASURY_USDC, Asset.USDC, context.transferAmountUsdc().multiply(new BigDecimal("2")),
                    first && scenario == Scenario.LOW_USDC ? context.transferAmountUsdc().subtract(new BigDecimal("0.000001")) : context.transferAmountUsdc()));
            pockets.add(pocket(PocketRole.GAS_ETH, Asset.ETH, context.requiredGasEth().multiply(new BigDecimal("2")),
                    first && scenario == Scenario.LOW_ETH ? BigDecimal.ZERO : context.requiredGasEth()));
            if (outcome != BeneficiaryOutcome.USDC_WALLET) {
                BigDecimal required = context.requiredPayoutAmounts().get(outcome);
                if (required == null || required.signum() <= 0) throw new IllegalArgumentException("Simulated bank scenario needs an explicit payout quote amount");
                pockets.add(pocket(PocketRole.PROVIDER_FIAT, outcome == BeneficiaryOutcome.SIMULATED_BANK_USD ? Asset.USD : Asset.INR,
                        required.multiply(new BigDecimal("2")), first && scenario == Scenario.LOW_FIAT ? BigDecimal.ZERO : required));
            }
            if (first && scenario == Scenario.CONTRADICTORY_LIQUIDITY) {
                Pocket old = pockets.getFirst();
                pockets.set(0, new Pocket(old.role(), old.asset(), old.total(), old.reserved(), old.total().add(BigDecimal.ONE)));
            }
            Availability availability = scenario == Scenario.NO_ELIGIBLE_ROUTE || first && scenario == Scenario.PROVIDER_OUTAGE
                    ? Availability.UNAVAILABLE : Availability.AVAILABLE;
            List<ExternalObservation> external = Arrays.stream(ExternalKind.values()).map(kind -> new ExternalObservation(kind,
                    switch (kind) { case NETWORK_CONGESTION -> ExternalUnit.MILLISECONDS;
                        case STABLECOIN_PRICE_DEVIATION -> ExternalUnit.BASIS_POINTS; default -> ExternalUnit.FLAG; },
                    kind == ExternalKind.NETWORK_CONGESTION && first && scenario == Scenario.CONGESTION ? new BigDecimal("600000") : BigDecimal.ZERO,
                    ExternalState.NORMAL, "SIMULATED-EXTERNAL-FEED", "fixture/" + scenario + "/" + kind, observed, observed.plusSeconds(120))).toList();
            List<SignalValue> values = List.of(new SuccessRate(990, 1000, Duration.ofHours(24), DeliveryScope.END_TO_END),
                    new Latency(first && scenario == Scenario.CONGESTION ? 600000 : first ? 120000 : 60000, 95, 1000,
                            Duration.ofHours(24), DeliveryScope.END_TO_END),
                    new TransactionCost(fees, total, CostBasis.SIMULATED_DELIVERY_ESTIMATE),
                    new FraudRisk(new BigDecimal(first && scenario == Scenario.FRAUD_BREACH ? "95" : "10"), "SIMULATED-FRAUD-v1", "fixture/screening/" + variant),
                    new RailAvailability(Availability.AVAILABLE, Availability.AVAILABLE, availability, Availability.AVAILABLE),
                    new Liquidity(pockets), new ExternalData(external));
            for (SignalValue value : values) {
                Unit unit = switch (value.type()) {
                    case SUCCESS_RATE -> Unit.RATIO; case LATENCY -> Unit.MILLISECONDS; case TRANSACTION_COST -> Unit.USD;
                    case FRAUD_RISK -> Unit.SCORE_0_100; case RAIL_AVAILABILITY -> Unit.STATUS;
                    case LIQUIDITY -> Unit.ASSET_AMOUNTS; case EXTERNAL_DATA -> Unit.TYPED_EXTERNAL;
                };
                observations.add(new Observation("SIMULATED/" + snapshotId + "/" + variant + "/" + value.type(), 1, value.type(), candidateId,
                        new Scope(context.customerId(), context.corridor(), context.sourceAmountUsd()), "SIMULATED-SCENARIO-FEED",
                        "fixture/" + scenario + "/" + variant + "/" + value.type(), observed, observed.plusSeconds(1), observed.plusSeconds(120),
                        unit, EvidenceMode.SIMULATED, Quality.VALID, network, value));
            }
        }
        return new Snapshot(snapshotId, 1, candidates, observations);
    }
    private static Pocket pocket(PocketRole role, Asset asset, BigDecimal total, BigDecimal available) {
        return new Pocket(role, asset, total, total.subtract(available), available);
    }
}
