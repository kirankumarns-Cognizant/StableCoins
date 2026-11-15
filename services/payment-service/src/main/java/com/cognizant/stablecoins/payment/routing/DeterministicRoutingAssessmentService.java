package com.cognizant.stablecoins.payment.routing;

import com.cognizant.stablecoins.payment.domain.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.exception.PaymentAccessException;
import com.cognizant.stablecoins.payment.exception.PaymentValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.routing.RoutingEvidence.*;
import static com.cognizant.stablecoins.payment.routing.AssessmentResult.*;

/** Read-only, deterministic baseline inside payment-service. No model, network calls or writes. */
@Service
public class DeterministicRoutingAssessmentService {
    private final AssessmentRules rules;
    private final Network configuredNetwork;
    private final Clock clock;
    private static final Set<ReasonCode> UNCERTAIN = EnumSet.of(ReasonCode.MISSING_SIGNAL,
            ReasonCode.DUPLICATE_SIGNAL, ReasonCode.UNSUPPORTED_SCHEMA, ReasonCode.INVALID_SIGNAL,
            ReasonCode.UNKNOWN_SIGNAL, ReasonCode.CONTRADICTORY_SIGNAL, ReasonCode.SCOPE_MISMATCH,
            ReasonCode.NETWORK_MISMATCH, ReasonCode.UNIT_MISMATCH, ReasonCode.SIGNAL_TYPE_MISMATCH,
            ReasonCode.FUTURE_EVIDENCE, ReasonCode.STALE_EVIDENCE, ReasonCode.INCOMPATIBLE_HISTORY,
            ReasonCode.INSUFFICIENT_HISTORY, ReasonCode.CHAIN_ONLY_ESTIMATE, ReasonCode.COST_COMPONENTS_INCOMPLETE,
            ReasonCode.COST_TOTAL_MISMATCH, ReasonCode.INVALID_CONVERSION, ReasonCode.UNSUPPORTED_COST_BASIS,
            ReasonCode.MISSING_PAYOUT_REQUIREMENT);

    @Autowired
    public DeterministicRoutingAssessmentService(AssessmentRules rules, Network configuredNetwork) {
        this(rules, configuredNetwork, Clock.systemUTC());
    }
    public DeterministicRoutingAssessmentService(AssessmentRules rules, Network configuredNetwork, Clock clock) {
        this.rules = Objects.requireNonNull(rules); this.configuredNetwork = Objects.requireNonNull(configuredNetwork);
        this.clock = Objects.requireNonNull(clock);
    }

    public AssessmentResult assess(AuthorizedCustomer actor, AssessmentContext context,
            PaymentIntentSnapshot intent, Snapshot snapshot) {
        validate(actor, context, intent, snapshot);
        Instant now = clock.instant();
        List<CandidateAssessment> assessed = snapshot.candidates().stream()
                .sorted(Comparator.comparing(Candidate::candidateId))
                .map(candidate -> assessCandidate(context, intent, candidate, snapshot.observations(), now)).toList();
        Comparator<CandidateAssessment> cost = Comparator.comparing(CandidateAssessment::totalCostUsd);
        Comparator<CandidateAssessment> latency = Comparator.comparing(CandidateAssessment::deliveryMillis);
        Comparator<CandidateAssessment> order = intent.instruction().priority() == RoutingPriority.LOWEST_COST
                ? cost.thenComparing(latency) : latency.thenComparing(cost);
        order = order.thenComparing(CandidateAssessment::successRate, Comparator.reverseOrder())
                .thenComparing(CandidateAssessment::fraudScore).thenComparing(a -> a.candidate().candidateId());
        var ranked = new EnumMap<BeneficiaryOutcome, List<String>>(BeneficiaryOutcome.class);
        for (BeneficiaryOutcome outcome : BeneficiaryOutcome.values()) {
            List<String> ids = assessed.stream().filter(a -> a.eligibility() == Eligibility.ELIGIBLE && a.candidate().outcome() == outcome)
                    .sorted(order).map(a -> a.candidate().candidateId()).toList();
            if (!ids.isEmpty()) ranked.put(outcome, ids);
        }
        String preferred = null;
        Status status;
        if (ranked.containsKey(intent.instruction().requiredOutcome())) {
            preferred = ranked.get(intent.instruction().requiredOutcome()).getFirst(); status = Status.ASSESSED;
        } else if (ranked.size() == 1) {
            preferred = ranked.values().iterator().next().getFirst(); status = Status.ASSESSED;
        } else if (ranked.size() > 1) {
            status = Status.OUTCOME_CHOICE_REQUIRED;
        } else {
            status = assessed.isEmpty() || assessed.stream().anyMatch(a -> a.eligibility() == Eligibility.INSUFFICIENT_EVIDENCE)
                    ? Status.INSUFFICIENT_EVIDENCE : Status.NO_ELIGIBLE_ROUTE;
        }
        Instant expiry = assessed.stream().filter(a -> a.eligibility() == Eligibility.ELIGIBLE)
                .map(CandidateAssessment::validUntil).min(Instant::compareTo).orElse(now);
        return new AssessmentResult(context.paymentId(), context.correlationId(), intent.intentId(), intent.intentVersion(),
                intent.policy().version(), rules.version(), snapshot.snapshotId(), now, expiry,
                intent.instruction().priority(), status, preferred, ranked, assessed);
    }

    private void validate(AuthorizedCustomer actor, AssessmentContext context, PaymentIntentSnapshot intent, Snapshot snapshot) {
        if (actor == null) throw new PaymentAccessException("AUTHENTICATION_REQUIRED", "Assessment requires verified customer identity");
        if (context == null || intent == null || !actor.customerId().equals(context.customerId())
                || !actor.customerId().equals(intent.customerId())) {
            throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH", "Assessment is outside the authorized customer scope");
        }
        if (blank(context.paymentId()) || blank(context.correlationId()) || blank(context.corridor())
                || !positive(context.sourceAmountUsd(), Asset.USD) || !positive(context.transferAmountUsdc(), Asset.USDC)
                || !positive(context.requiredGasEth(), Asset.ETH) || context.requestedRail() == null
                || intent.intentId() == null || intent.intentVersion() < 1 || intent.instruction() == null || intent.policy() == null
                || intent.settlementAsset() != SettlementAsset.USDC || !intent.fundingSimulated()) {
            throw invalid("INVALID_ASSESSMENT_CONTEXT");
        }
        var instruction = intent.instruction();
        if (instruction.requiredOutcome() == null || instruction.deliverBy() == null || instruction.priority() == null
                || instruction.allowedAlternatives() == null || !Boolean.TRUE.equals(instruction.confirmed())
                || instruction.feeCurrency() != SupportedCurrency.USD || !amount(instruction.maximumFee(), Asset.USD)
                || instruction.maximumFee().compareTo(intent.policy().maximumFeeUsd()) > 0
                || !intent.policy().allowedOutcomes().contains(instruction.requiredOutcome())
                || !intent.policy().allowedOutcomes().containsAll(instruction.allowedAlternatives())
                || instruction.allowedAlternatives().contains(instruction.requiredOutcome())
                || context.requestedRail() != PaymentRail.AUTO && !instruction.allowedAlternatives().isEmpty()
                || !intent.fiatPayoutSimulated() && (instruction.requiredOutcome() != BeneficiaryOutcome.USDC_WALLET
                    || instruction.allowedAlternatives().stream().anyMatch(o -> o != BeneficiaryOutcome.USDC_WALLET))) {
            throw invalid("INVALID_ASSESSMENT_CONTEXT");
        }
        if (snapshot == null || snapshot.snapshotId() == null || snapshot.schemaVersion() != 1
                || snapshot.candidates().size() > rules.maximumCandidates() || snapshot.observations().size() > rules.maximumObservations()) {
            throw invalid("INVALID_EVIDENCE_SNAPSHOT");
        }
        Set<String> candidates = new HashSet<>(), signals = new HashSet<>();
        for (Candidate candidate : snapshot.candidates()) {
            if (blank(candidate.candidateId()) || blank(candidate.providerId()) || !candidates.add(candidate.candidateId()))
                throw invalid("INVALID_EVIDENCE_SNAPSHOT");
        }
        for (Observation observation : snapshot.observations()) {
            if (observation.scope() != null && !actor.customerId().equals(observation.scope().customerId()))
                throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH", "Evidence is outside the authorized customer scope");
            if (blank(observation.signalId()) || !signals.add(observation.signalId()) || !candidates.contains(observation.candidateId()))
                throw invalid("INVALID_EVIDENCE_SNAPSHOT");
        }
    }

    private CandidateAssessment assessCandidate(AssessmentContext context, PaymentIntentSnapshot intent,
            Candidate candidate, List<Observation> all, Instant now) {
        Evaluation e = new Evaluation(candidate, intent.instruction().deliverBy());
        boolean wallet = candidate.outcome() == BeneficiaryOutcome.USDC_WALLET;
        if (candidate.outcome() == null || (wallet ? candidate.rail() != PaymentRail.STABLECOIN : candidate.rail() != PaymentRail.STABLECOIN_PLUS_VISA))
            e.add(ReasonCode.UNSUPPORTED_PATH, null);
        if (candidate.outcome() == null || !intent.policy().allowedOutcomes().contains(candidate.outcome()) || candidate.outcome() != intent.instruction().requiredOutcome()
                && !intent.instruction().allowedAlternatives().contains(candidate.outcome())) e.add(ReasonCode.OUTCOME_NOT_ALLOWED, null);
        if (!intent.policy().allowedProviderIds().contains(candidate.providerId())) e.add(ReasonCode.PROVIDER_NOT_ALLOWED, null);
        if (context.requestedRail() != PaymentRail.AUTO && context.requestedRail() != candidate.rail()) e.add(ReasonCode.REQUESTED_RAIL_MISMATCH, null);
        if (!supported(candidate.network()) || !sameNetwork(candidate.network(), configuredNetwork)) e.add(ReasonCode.UNSUPPORTED_NETWORK, null);
        if (!intent.instruction().deliverBy().isAfter(now)) e.add(ReasonCode.DEADLINE_EXCEEDED, null);
        List<Observation> observations = all.stream().filter(o -> o.candidateId().equals(candidate.candidateId())).toList();
        for (Observation o : observations) {
            e.references.add(new EvidenceReference(o.signalId(), o.signalType(), o.sourceId(), o.sourceRecordReference(),
                    o.evidenceMode(), o.quality(), o.observedAt(), o.receivedAt(), o.expiresAt()));
            if (o.evidenceMode() != null) e.modes.add(o.evidenceMode());
            if (o.signalType() == null) e.add(ReasonCode.INVALID_SIGNAL, o);
        }
        for (SignalType type : SignalType.values()) {
            List<Observation> matches = observations.stream().filter(o -> o.signalType() == type).toList();
            if (matches.isEmpty()) { e.findings.add(new Finding(ReasonCode.MISSING_SIGNAL, type, null)); continue; }
            if (matches.size() > 1) { e.add(ReasonCode.DUPLICATE_SIGNAL, matches.getFirst()); continue; }
            Observation o = matches.getFirst();
            if (!metadata(context, candidate, o, now, e)) continue;
            switch (o.value()) {
                case SuccessRate value -> success(value, o, e);
                case Latency value -> latency(value, o, now, intent, e);
                case TransactionCost value -> costs(value, o, now, intent, context, e);
                case FraudRisk value -> fraud(value, o, e);
                case RailAvailability value -> availability(value, o, e);
                case Liquidity value -> liquidity(value, o, context, candidate, e);
                case ExternalData value -> external(value, o, now, e);
            }
        }
        return e.result();
    }

    private boolean metadata(AssessmentContext context, Candidate candidate, Observation o, Instant now, Evaluation e) {
        int before = e.findings.size();
        if (o.schemaVersion() != 1) e.add(ReasonCode.UNSUPPORTED_SCHEMA, o);
        if (o.quality() == Quality.UNKNOWN) e.add(ReasonCode.UNKNOWN_SIGNAL, o);
        else if (o.quality() == Quality.CONTRADICTORY) e.add(ReasonCode.CONTRADICTORY_SIGNAL, o);
        else if (o.quality() != Quality.VALID) e.add(ReasonCode.INVALID_SIGNAL, o);
        if (o.value() == null || o.evidenceMode() == null || blank(o.sourceId()) || blank(o.sourceRecordReference())) e.add(ReasonCode.INVALID_SIGNAL, o);
        if (o.value() != null && o.value().type() != o.signalType()) e.add(ReasonCode.SIGNAL_TYPE_MISMATCH, o);
        Unit expected = switch (o.signalType()) {
            case SUCCESS_RATE -> Unit.RATIO; case LATENCY -> Unit.MILLISECONDS; case TRANSACTION_COST -> Unit.USD;
            case FRAUD_RISK -> Unit.SCORE_0_100; case RAIL_AVAILABILITY -> Unit.STATUS;
            case LIQUIDITY -> Unit.ASSET_AMOUNTS; case EXTERNAL_DATA -> Unit.TYPED_EXTERNAL;
        };
        if (o.unit() != expected) e.add(ReasonCode.UNIT_MISMATCH, o);
        if (candidate.outcome() != BeneficiaryOutcome.USDC_WALLET && o.evidenceMode() != EvidenceMode.SIMULATED
                && EnumSet.of(SignalType.SUCCESS_RATE, SignalType.LATENCY, SignalType.RAIL_AVAILABILITY, SignalType.EXTERNAL_DATA).contains(o.signalType()))
            e.add(ReasonCode.SIMULATED_PAYOUT_EVIDENCE_REQUIRED, o);
        if (o.scope() == null || !context.corridor().equals(o.scope().corridor()) || o.scope().sourceAmountUsd() == null
                || context.sourceAmountUsd().compareTo(o.scope().sourceAmountUsd()) != 0) e.add(ReasonCode.SCOPE_MISMATCH, o);
        if (o.network() == null || !sameNetwork(o.network(), candidate.network())) e.add(ReasonCode.NETWORK_MISMATCH, o);
        timestamps(o.observedAt(), o.expiresAt(), o, now, e);
        if (o.receivedAt() == null || o.observedAt() == null || o.receivedAt().isBefore(o.observedAt())) e.add(ReasonCode.INVALID_SIGNAL, o);
        else if (o.receivedAt().isAfter(now)) e.add(ReasonCode.FUTURE_EVIDENCE, o);
        return before == e.findings.size();
    }
    private void timestamps(Instant observed, Instant expires, Observation o, Instant now, Evaluation e) {
        if (observed == null || expires == null || !expires.isAfter(observed)) { e.add(ReasonCode.INVALID_SIGNAL, o); return; }
        if (observed.isAfter(now)) { e.add(ReasonCode.FUTURE_EVIDENCE, o); return; }
        if (!expires.isAfter(now) || !observed.isAfter(now.minus(rules.maximumEvidenceAge()))) e.add(ReasonCode.STALE_EVIDENCE, o);
        e.until(expires); e.until(observed.plus(rules.maximumEvidenceAge()));
    }
    private void success(SuccessRate v, Observation o, Evaluation e) {
        if (v.attempts() <= 0 || v.completed() < 0 || v.completed() > v.attempts() || v.window() == null || v.deliveryScope() == null) {
            e.add(ReasonCode.INVALID_SIGNAL, o); return;
        }
        if (v.deliveryScope() != DeliveryScope.END_TO_END) e.add(ReasonCode.CHAIN_ONLY_ESTIMATE, o);
        if (!rules.historicalWindow().equals(v.window())) e.add(ReasonCode.INCOMPATIBLE_HISTORY, o);
        if (v.attempts() < rules.minimumSampleSize()) { e.add(ReasonCode.INSUFFICIENT_HISTORY, o); return; }
        e.success = BigDecimal.valueOf(v.completed()).divide(BigDecimal.valueOf(v.attempts()), MathContext.DECIMAL128);
        if (BigDecimal.valueOf(v.completed()).compareTo(rules.minimumSuccessRate().multiply(BigDecimal.valueOf(v.attempts()))) < 0)
            e.add(ReasonCode.SUCCESS_RATE_BELOW_MINIMUM, o);
    }
    private void latency(Latency v, Observation o, Instant now, PaymentIntentSnapshot intent, Evaluation e) {
        if (v.estimatedDeliveryMillis() <= 0 || v.sampleSize() < 0 || v.window() == null || v.deliveryScope() == null) { e.add(ReasonCode.INVALID_SIGNAL, o); return; }
        if (v.deliveryScope() != DeliveryScope.END_TO_END) e.add(ReasonCode.CHAIN_ONLY_ESTIMATE, o);
        if (!rules.historicalWindow().equals(v.window()) || v.percentile() != rules.latencyPercentile()) e.add(ReasonCode.INCOMPATIBLE_HISTORY, o);
        if (v.sampleSize() < rules.minimumSampleSize()) e.add(ReasonCode.INSUFFICIENT_HISTORY, o);
        e.latency = v.estimatedDeliveryMillis();
        if (Duration.ofMillis(e.latency).compareTo(Duration.between(now, intent.instruction().deliverBy())) > 0) e.add(ReasonCode.DEADLINE_EXCEEDED, o);
        else e.until(intent.instruction().deliverBy().minusMillis(e.latency));
    }
    private void costs(TransactionCost v, Observation o, Instant now, PaymentIntentSnapshot intent, AssessmentContext context, Evaluation e) {
        if (v.basis() != CostBasis.SIMULATED_DELIVERY_ESTIMATE || o.evidenceMode() != EvidenceMode.SIMULATED) {
            e.add(ReasonCode.UNSUPPORTED_COST_BASIS, o); return;
        }
        Set<FeeComponent> seen = EnumSet.noneOf(FeeComponent.class);
        BigDecimal total = BigDecimal.ZERO;
        for (CostItem item : v.items()) {
            if (item.component() == null || !seen.add(item.component())) { e.add(ReasonCode.COST_COMPONENTS_INCOMPLETE, o); continue; }
            if (!amount(item.nativeAmount(), item.nativeAsset()) || !amount(item.comparisonAmountUsd(), Asset.USD)) { e.add(ReasonCode.INVALID_SIGNAL, o); continue; }
            if (item.component() == FeeComponent.GAS && (item.nativeAsset() != Asset.ETH || item.nativeAmount().signum() <= 0
                    || item.nativeAmount().compareTo(context.requiredGasEth()) > 0)) e.add(ReasonCode.CONTRADICTORY_SIGNAL, o);
            BigDecimal normalized = item.nativeAmount();
            if (item.nativeAsset() != Asset.USD) {
                ConversionQuote quote = item.conversion();
                if (quote == null || blank(quote.quoteId()) || quote.from() != item.nativeAsset() || !positive(quote.usdPerUnit(), Asset.ETH) || !quote.simulatedBudgetRate()) { e.add(ReasonCode.INVALID_CONVERSION, o); continue; }
                timestamps(quote.observedAt(), quote.expiresAt(), o, now, e);
                normalized = normalized.multiply(quote.usdPerUnit()).setScale(2, RoundingMode.HALF_EVEN);
            } else if (item.conversion() != null) { e.add(ReasonCode.INVALID_CONVERSION, o); continue; }
            if (normalized.compareTo(item.comparisonAmountUsd()) != 0) e.add(ReasonCode.INVALID_CONVERSION, o);
            total = total.add(item.comparisonAmountUsd());
        }
        if (seen.size() != FeeComponent.values().length) e.add(ReasonCode.COST_COMPONENTS_INCOMPLETE, o);
        if (!amount(v.declaredTotalUsd(), Asset.USD) || total.compareTo(v.declaredTotalUsd()) != 0) e.add(ReasonCode.COST_TOTAL_MISMATCH, o);
        e.cost = total; e.costItems = v.items();
        if (total.compareTo(intent.instruction().maximumFee()) > 0 || total.compareTo(intent.policy().maximumFeeUsd()) > 0) e.add(ReasonCode.FEE_LIMIT_EXCEEDED, o);
    }
    private void fraud(FraudRisk v, Observation o, Evaluation e) {
        if (v.score() == null || v.score().signum() < 0 || v.score().compareTo(new BigDecimal("100")) > 0
                || blank(v.modelVersion()) || blank(v.screeningReference())) { e.add(ReasonCode.INVALID_SIGNAL, o); return; }
        if (o.evidenceMode() != EvidenceMode.SIMULATED) e.add(ReasonCode.SIMULATED_SCREENING_REQUIRED, o);
        e.fraud = v.score();
        if (v.score().compareTo(rules.maximumFraudScore()) > 0) e.add(ReasonCode.FRAUD_LIMIT_EXCEEDED, o);
    }
    private void availability(RailAvailability v, Observation o, Evaluation e) {
        Availability[] statuses = {v.network(), v.rpc(), v.provider(), v.beneficiaryDelivery()};
        if (Arrays.stream(statuses).anyMatch(Objects::isNull)) { e.add(ReasonCode.INVALID_SIGNAL, o); return; }
        if (Arrays.asList(statuses).contains(Availability.UNKNOWN)) e.add(ReasonCode.UNKNOWN_SIGNAL, o);
        if (Arrays.asList(statuses).contains(Availability.UNAVAILABLE)) e.add(ReasonCode.RAIL_UNAVAILABLE, o);
        if (Arrays.asList(statuses).contains(Availability.DEGRADED)) e.add(ReasonCode.RAIL_DEGRADED, o);
    }
    private void liquidity(Liquidity v, Observation o, AssessmentContext context, Candidate candidate, Evaluation e) {
        Map<PocketRole, Pocket> pockets = new EnumMap<>(PocketRole.class);
        for (Pocket pocket : v.pockets()) {
            if (pocket.role() == null || pockets.putIfAbsent(pocket.role(), pocket) != null) { e.add(ReasonCode.INVALID_SIGNAL, o); continue; }
            if (!amount(pocket.total(), pocket.asset()) || !amount(pocket.reserved(), pocket.asset()) || !amount(pocket.available(), pocket.asset())
                    || pocket.total().subtract(pocket.reserved()).compareTo(pocket.available()) != 0) e.add(ReasonCode.CONTRADICTORY_SIGNAL, o);
        }
        checkPocket(pockets.get(PocketRole.TREASURY_USDC), Asset.USDC, context.transferAmountUsdc(), ReasonCode.INSUFFICIENT_USDC, o, e);
        checkPocket(pockets.get(PocketRole.GAS_ETH), Asset.ETH, context.requiredGasEth(), ReasonCode.INSUFFICIENT_ETH, o, e);
        if (candidate.outcome() == BeneficiaryOutcome.USDC_WALLET && pockets.containsKey(PocketRole.PROVIDER_FIAT)) e.add(ReasonCode.INVALID_SIGNAL, o);
        if (candidate.outcome() != BeneficiaryOutcome.USDC_WALLET && candidate.outcome() != null) {
            Asset asset = candidate.outcome() == BeneficiaryOutcome.SIMULATED_BANK_USD ? Asset.USD : Asset.INR;
            BigDecimal required = context.requiredPayoutAmounts().get(candidate.outcome());
            if (!positive(required, asset)) e.add(ReasonCode.MISSING_PAYOUT_REQUIREMENT, o);
            else checkPocket(pockets.get(PocketRole.PROVIDER_FIAT), asset, required, ReasonCode.INSUFFICIENT_FIAT, o, e);
            // Payout-provider liquidity is simulated in this POC, including when chain evidence is live.
            if (o.evidenceMode() != EvidenceMode.SIMULATED) e.add(ReasonCode.INVALID_SIGNAL, o);
        }
    }
    private void checkPocket(Pocket pocket, Asset asset, BigDecimal required, ReasonCode shortage, Observation o, Evaluation e) {
        if (pocket == null || pocket.asset() != asset) e.add(ReasonCode.INVALID_SIGNAL, o);
        else if (pocket.available() != null && pocket.available().compareTo(required) < 0) e.add(shortage, o);
    }
    private void external(ExternalData v, Observation o, Instant now, Evaluation e) {
        Set<ExternalKind> seen = EnumSet.noneOf(ExternalKind.class);
        for (ExternalObservation item : v.observations()) {
            if (item.kind() == null || !seen.add(item.kind()) || item.state() == null || blank(item.sourceId()) || blank(item.sourceRecordReference())
                    || item.magnitude() == null || item.magnitude().signum() < 0) { e.add(ReasonCode.INVALID_SIGNAL, o); continue; }
            ExternalUnit expected = switch (item.kind()) {
                case NETWORK_CONGESTION -> ExternalUnit.MILLISECONDS;
                case STABLECOIN_PRICE_DEVIATION -> ExternalUnit.BASIS_POINTS;
                case PROVIDER_INCIDENT, CALENDAR_CUTOFF -> ExternalUnit.FLAG;
            };
            if (item.unit() != expected || expected == ExternalUnit.FLAG && item.magnitude().compareTo(BigDecimal.ZERO) != 0 && item.magnitude().compareTo(BigDecimal.ONE) != 0) e.add(ReasonCode.UNIT_MISMATCH, o);
            timestamps(item.observedAt(), item.expiresAt(), o, now, e);
            if (item.state() == ExternalState.UNKNOWN) e.add(ReasonCode.UNKNOWN_SIGNAL, o);
            if (item.state() == ExternalState.BLOCKING || expected == ExternalUnit.FLAG && item.magnitude().signum() > 0) e.add(ReasonCode.EXTERNAL_BLOCKER, o);
            if (item.kind() == ExternalKind.STABLECOIN_PRICE_DEVIATION && item.magnitude().compareTo(rules.maximumPriceDeviationBps()) > 0)
                e.add(ReasonCode.PRICE_DEVIATION_EXCEEDED, o);
        }
        if (seen.size() != ExternalKind.values().length) e.add(ReasonCode.INVALID_SIGNAL, o);
    }

    private static boolean sameNetwork(Network a, Network b) {
        return a != null && b != null && Objects.equals(a.name(), b.name()) && a.chainId() == b.chainId()
                && a.testnet() == b.testnet() && a.tokenContract() != null && b.tokenContract() != null
                && a.tokenContract().equalsIgnoreCase(b.tokenContract());
    }
    private static boolean supported(Network network) {
        return network != null && network.testnet() && network.tokenContract() != null && network.tokenContract().matches("0x[0-9a-fA-F]{40}")
                && ("base-sepolia".equals(network.name()) && network.chainId() == 84532
                || "ethereum-sepolia".equals(network.name()) && network.chainId() == 11155111);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean positive(BigDecimal value, Asset asset) { return amount(value, asset) && value.signum() > 0; }
    private static boolean amount(BigDecimal value, Asset asset) {
        if (value == null || asset == null || value.signum() < 0 || (long) value.precision() - value.scale() > 24) return false;
        int decimals = switch (asset) { case USD, INR -> 2; case USDC -> 6; case ETH -> 18; };
        return value.stripTrailingZeros().scale() <= decimals;
    }
    private static PaymentValidationException invalid(String code) { return new PaymentValidationException(code, "Invalid server assessment input"); }

    private static final class Evaluation {
        final Candidate candidate;
        final List<Finding> findings = new ArrayList<>();
        final List<EvidenceReference> references = new ArrayList<>();
        final Set<EvidenceMode> modes = EnumSet.noneOf(EvidenceMode.class);
        Instant expiry;
        BigDecimal cost, success, fraud;
        Long latency;
        List<CostItem> costItems = List.of();
        Evaluation(Candidate candidate, Instant expiry) { this.candidate = candidate; this.expiry = expiry; }
        void add(ReasonCode code, Observation o) {
            Finding finding = new Finding(code, o == null ? null : o.signalType(), o == null ? null : o.signalId());
            if (!findings.contains(finding)) findings.add(finding);
        }
        void until(Instant time) { if (time.isBefore(expiry)) expiry = time; }
        CandidateAssessment result() {
            Eligibility status = findings.isEmpty() ? Eligibility.ELIGIBLE
                    : findings.stream().anyMatch(f -> !UNCERTAIN.contains(f.code())) ? Eligibility.INELIGIBLE : Eligibility.INSUFFICIENT_EVIDENCE;
            return new CandidateAssessment(candidate, status, findings, cost, costItems, latency, success, fraud, expiry, modes, references);
        }
    }
}
