package com.cognizant.stablecoins.payment.routing;

import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import com.cognizant.stablecoins.payment.domain.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.exception.*;
import com.cognizant.stablecoins.payment.support.IntentFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.payment.routing.RoutingEvidence.*;
import static com.cognizant.stablecoins.payment.routing.AssessmentResult.*;
import static com.cognizant.stablecoins.payment.routing.SimulatedRoutingScenarios.Scenario;

class DeterministicRoutingAssessmentServiceTest {
    static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    static final Network NETWORK = new Network("base-sepolia", 84532, "0x036CbD53842c5426634e7929541eC2318f3dCF7e", true);
    static final AssessmentRules RULES = new AssessmentRules("test-rules-v1", Duration.ofMinutes(5), Duration.ofHours(24),
            100, 95, new BigDecimal("0.95"), new BigDecimal("30"), new BigDecimal("100"), 20, 200);
    final DeterministicRoutingAssessmentService service = new DeterministicRoutingAssessmentService(RULES, NETWORK, Clock.fixed(NOW, ZoneOffset.UTC));
    final AssessmentContext context = new AssessmentContext("PAY-TEST-1", "CORR-TEST-1", IntentFixtures.CUSTOMER, "US-IN",
            new BigDecimal("10000.00"), new BigDecimal("10000"), new BigDecimal("0.0002"),
            Map.of(BeneficiaryOutcome.SIMULATED_BANK_INR, new BigDecimal("830000.00"), BeneficiaryOutcome.SIMULATED_BANK_USD, new BigDecimal("10000.00")), PaymentRail.AUTO);

    PaymentIntentSnapshot intent(RoutingPriority priority, BeneficiaryOutcome outcome, Set<BeneficiaryOutcome> alternatives) {
        return new PaymentIntentSnapshot(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), 1, IntentFixtures.CUSTOMER,
                "verified-test-subject", NOW.minusSeconds(10), SettlementAsset.USDC, true, true,
                new PaymentIntentRequest(outcome, NOW.plusSeconds(300), new BigDecimal("25.00"), SupportedCurrency.USD, priority, alternatives, true),
                IntentFixtures.policy("customer-policy-v1"));
    }
    PaymentIntentSnapshot intent() { return intent(RoutingPriority.LOWEST_COST, BeneficiaryOutcome.SIMULATED_BANK_INR, Set.of()); }
    Snapshot fixture(Scenario scenario) { return SimulatedRoutingScenarios.create(scenario, context, BeneficiaryOutcome.SIMULATED_BANK_INR, NETWORK, NOW); }
    AssessmentResult assess(Snapshot snapshot) { return service.assess(IntentFixtures.ACTOR, context, intent(), snapshot); }
    Snapshot change(SignalType type, UnaryOperator<Observation> edit) {
        Snapshot s = fixture(Scenario.NORMAL);
        return new Snapshot(s.snapshotId(), 1, s.candidates(), s.observations().stream()
                .map(o -> o.candidateId().equals("SIMULATED-A") && o.signalType() == type ? edit.apply(o) : o).toList());
    }
    Observation value(Observation o, SignalValue value) {
        return new Observation(o.signalId(), o.schemaVersion(), o.signalType(), o.candidateId(), o.scope(), o.sourceId(), o.sourceRecordReference(),
                o.observedAt(), o.receivedAt(), o.expiresAt(), o.unit(), o.evidenceMode(), o.quality(), o.network(), value);
    }
    Observation metadata(Observation o, Scope scope, Instant observed, Instant received, Instant expires, Unit unit, Quality quality, Network network) {
        return new Observation(o.signalId(), o.schemaVersion(), o.signalType(), o.candidateId(), scope, o.sourceId(), o.sourceRecordReference(),
                observed, received, expires, unit, o.evidenceMode(), quality, network, o.value());
    }
    CandidateAssessment first(AssessmentResult result) { return result.candidates().stream().filter(a -> a.candidate().candidateId().equals("SIMULATED-A")).findFirst().orElseThrow(); }
    void issue(Snapshot snapshot, ReasonCode reason) {
        CandidateAssessment a = first(assess(snapshot));
        assertThat(a.eligibility()).isNotEqualTo(Eligibility.ELIGIBLE);
        assertThat(a.findings()).extracting(Finding::code).contains(reason);
    }

    @Test void lowestCostUsesFullItemizationAndKeepsEvidence() {
        AssessmentResult result = assess(fixture(Scenario.NORMAL));
        assertThat(result.status()).isEqualTo(Status.ASSESSED);
        assertThat(result.preferredCandidateId()).isEqualTo("SIMULATED-A");
        assertThat(first(result).totalCostUsd()).isEqualByComparingTo("5.30");
        assertThat(first(result).itemizedCosts()).hasSize(7);
        assertThat(first(result).evidence()).hasSize(7);
        assertThat(first(result).evidenceModes()).containsExactly(EvidenceMode.SIMULATED);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(115));
        assertThat(result.ruleVersion()).isEqualTo("test-rules-v1");
        assertThat(result.policyVersion()).isEqualTo("customer-policy-v1");
        assertThat(result.paymentId()).isEqualTo("PAY-TEST-1");
        assertThat(result.correlationId()).isEqualTo("CORR-TEST-1");
    }
    @Test void meetDeadlineRanksFastestEligibleEquivalentOutcome() {
        var result = service.assess(IntentFixtures.ACTOR, context, intent(RoutingPriority.MEET_DEADLINE, BeneficiaryOutcome.SIMULATED_BANK_INR, Set.of()), fixture(Scenario.NORMAL));
        assertThat(result.preferredCandidateId()).isEqualTo("SIMULATED-B");
    }
    static Stream<Arguments> disruptions() {
        return Stream.of(Arguments.of(Scenario.CONGESTION, ReasonCode.DEADLINE_EXCEEDED),
                Arguments.of(Scenario.PROVIDER_OUTAGE, ReasonCode.RAIL_UNAVAILABLE),
                Arguments.of(Scenario.LOW_USDC, ReasonCode.INSUFFICIENT_USDC), Arguments.of(Scenario.LOW_ETH, ReasonCode.INSUFFICIENT_ETH),
                Arguments.of(Scenario.LOW_FIAT, ReasonCode.INSUFFICIENT_FIAT), Arguments.of(Scenario.FRAUD_BREACH, ReasonCode.FRAUD_LIMIT_EXCEEDED),
                Arguments.of(Scenario.STALE_EVIDENCE, ReasonCode.STALE_EVIDENCE), Arguments.of(Scenario.CONTRADICTORY_LIQUIDITY, ReasonCode.CONTRADICTORY_SIGNAL));
    }
    @ParameterizedTest @MethodSource("disruptions") void controlledDisruptionExcludesAffectedProvider(Scenario scenario, ReasonCode reason) {
        Snapshot snapshot = fixture(scenario); issue(snapshot, reason);
        assertThat(assess(snapshot).preferredCandidateId()).isEqualTo("SIMULATED-B");
    }
    @Test void noEligibleRouteDoesNotForceAChoice() {
        var result = assess(fixture(Scenario.NO_ELIGIBLE_ROUTE));
        assertThat(result.status()).isEqualTo(Status.NO_ELIGIBLE_ROUTE);
        assertThat(result.preferredCandidateId()).isNull(); assertThat(result.rankedCandidateIds()).isEmpty();
    }
    @Test void missingAllEvidenceReturnsInsufficientRatherThanZeroDefaults() {
        Snapshot s = fixture(Scenario.NORMAL);
        var result = assess(new Snapshot(s.snapshotId(), 1, s.candidates(), List.of()));
        assertThat(result.status()).isEqualTo(Status.INSUFFICIENT_EVIDENCE);
        assertThat(first(result).totalCostUsd()).isNull(); assertThat(first(result).deliveryMillis()).isNull();
        assertThat(result.preferredCandidateId()).isNull();
    }
    @Test void duplicateSignalsAreNotSilentlyResolved() {
        Snapshot s = fixture(Scenario.NORMAL); List<Observation> list = new ArrayList<>(s.observations()); Observation o = list.getFirst();
        list.add(new Observation("another-id", 1, o.signalType(), o.candidateId(), o.scope(), o.sourceId(), o.sourceRecordReference(),
                o.observedAt(), o.receivedAt(), o.expiresAt(), o.unit(), o.evidenceMode(), o.quality(), o.network(), o.value()));
        issue(new Snapshot(s.snapshotId(), 1, s.candidates(), list), ReasonCode.DUPLICATE_SIGNAL);
    }
    @ParameterizedTest @EnumSource(value=Quality.class, names={"UNKNOWN","INVALID","CONTRADICTORY"})
    void badQualityCannotBecomeEligible(Quality quality) {
        issue(change(SignalType.SUCCESS_RATE, o -> metadata(o,o.scope(),o.observedAt(),o.receivedAt(),o.expiresAt(),o.unit(),quality,o.network())),
                switch(quality) { case UNKNOWN -> ReasonCode.UNKNOWN_SIGNAL; case CONTRADICTORY -> ReasonCode.CONTRADICTORY_SIGNAL; default -> ReasonCode.INVALID_SIGNAL; });
    }
    @Test void futureObservationRejected() {
        issue(change(SignalType.LATENCY, o -> metadata(o,o.scope(),NOW.plusSeconds(1),NOW.plusSeconds(2),NOW.plusSeconds(60),o.unit(),o.quality(),o.network())),ReasonCode.FUTURE_EVIDENCE);
    }
    @Test void futureReceiptRejected() {
        issue(change(SignalType.LATENCY, o -> metadata(o,o.scope(),o.observedAt(),NOW.plusSeconds(2),o.expiresAt(),o.unit(),o.quality(),o.network())),ReasonCode.FUTURE_EVIDENCE);
    }
    @Test void expiredAtCurrentInstantIsStale() {
        issue(change(SignalType.LATENCY, o -> metadata(o,o.scope(),o.observedAt(),o.receivedAt(),NOW,o.unit(),o.quality(),o.network())),ReasonCode.STALE_EVIDENCE);
    }
    @Test void wrongCorridorRejected() {
        issue(change(SignalType.LATENCY, o -> metadata(o,new Scope(context.customerId(),"US-OTHER",context.sourceAmountUsd()),o.observedAt(),o.receivedAt(),o.expiresAt(),o.unit(),o.quality(),o.network())),ReasonCode.SCOPE_MISMATCH);
    }
    @Test void wrongAmountScopeRejected() {
        issue(change(SignalType.LATENCY, o -> metadata(o,new Scope(context.customerId(),context.corridor(),BigDecimal.ONE),o.observedAt(),o.receivedAt(),o.expiresAt(),o.unit(),o.quality(),o.network())),ReasonCode.SCOPE_MISMATCH);
    }
    @Test void wrongUnitsRejected() {
        issue(change(SignalType.LATENCY, o -> metadata(o,o.scope(),o.observedAt(),o.receivedAt(),o.expiresAt(),Unit.USD,o.quality(),o.network())),ReasonCode.UNIT_MISMATCH);
    }
    @Test void evidenceFromAnotherNetworkRejected() {
        issue(change(SignalType.LATENCY, o -> metadata(o,o.scope(),o.observedAt(),o.receivedAt(),o.expiresAt(),o.unit(),o.quality(),new Network("ethereum-sepolia",11155111,NETWORK.tokenContract(),true))),ReasonCode.NETWORK_MISMATCH);
    }
    @Test void valueMustMatchSignalType() {
        issue(change(SignalType.LATENCY, o -> value(o,new FraudRisk(BigDecimal.TEN,"v1","screen"))),ReasonCode.SIGNAL_TYPE_MISMATCH);
    }
    @Test void insufficientSuccessHistoryIsUnknown() {
        issue(change(SignalType.SUCCESS_RATE,o -> value(o,new SuccessRate(9,10,Duration.ofHours(24),DeliveryScope.END_TO_END))),ReasonCode.INSUFFICIENT_HISTORY);
    }
    @Test void lowSuccessRateIsIneligible() {
        issue(change(SignalType.SUCCESS_RATE,o -> value(o,new SuccessRate(900,1000,Duration.ofHours(24),DeliveryScope.END_TO_END))),ReasonCode.SUCCESS_RATE_BELOW_MINIMUM);
    }
    @Test void incompatibleHistoryWindowRejected() {
        issue(change(SignalType.SUCCESS_RATE,o -> value(o,new SuccessRate(990,1000,Duration.ofHours(1),DeliveryScope.END_TO_END))),ReasonCode.INCOMPATIBLE_HISTORY);
    }
    @Test void chainOnlyLatencyCannotStandInForBankCredit() {
        issue(change(SignalType.LATENCY,o -> value(o,new Latency(1000,95,1000,Duration.ofHours(24),DeliveryScope.CHAIN_ONLY))),ReasonCode.CHAIN_ONLY_ESTIMATE);
    }
    @Test void percentileMustMatchComparisonBaseline() {
        issue(change(SignalType.LATENCY,o -> value(o,new Latency(1000,50,1000,Duration.ofHours(24),DeliveryScope.END_TO_END))),ReasonCode.INCOMPATIBLE_HISTORY);
    }
    @Test void missingFeeComponentCannotMakeRouteCheaper() {
        issue(change(SignalType.TRANSACTION_COST,o -> { var v=(TransactionCost)o.value(); return value(o,new TransactionCost(v.items().subList(1,v.items().size()),v.declaredTotalUsd(),v.basis())); }),ReasonCode.COST_COMPONENTS_INCOMPLETE);
    }
    @Test void quotedTotalsMustMatchLineItems() {
        issue(change(SignalType.TRANSACTION_COST,o -> { var v=(TransactionCost)o.value(); return value(o,new TransactionCost(v.items(),BigDecimal.ZERO,v.basis())); }),ReasonCode.COST_TOTAL_MISMATCH);
    }
    @Test void duplicateFeesRejectedWithoutDoubleCounting() {
        issue(change(SignalType.TRANSACTION_COST,o -> { var v=(TransactionCost)o.value(); var items=new ArrayList<>(v.items()); items.add(items.getFirst()); return value(o,new TransactionCost(items,v.declaredTotalUsd(),v.basis())); }),ReasonCode.COST_COMPONENTS_INCOMPLETE);
    }
    @Test void expiredConversionQuoteRejected() {
        issue(change(SignalType.TRANSACTION_COST,o -> { var v=(TransactionCost)o.value(); var items=v.items().stream().map(i -> i.component()!=FeeComponent.GAS?i:new CostItem(i.component(),i.nativeAmount(),i.nativeAsset(),i.comparisonAmountUsd(),new ConversionQuote("old",Asset.ETH,new BigDecimal("2000"),NOW.minusSeconds(30),NOW,true))).toList(); return value(o,new TransactionCost(items,v.declaredTotalUsd(),v.basis())); }),ReasonCode.STALE_EVIDENCE);
    }
    @Test void testTokenValuationMustRemainSimulated() {
        issue(change(SignalType.TRANSACTION_COST,o -> { var v=(TransactionCost)o.value(); var items=v.items().stream().map(i -> i.component()!=FeeComponent.GAS?i:new CostItem(i.component(),i.nativeAmount(),i.nativeAsset(),i.comparisonAmountUsd(),new ConversionQuote("false-live",Asset.ETH,new BigDecimal("2000"),NOW.minusSeconds(5),NOW.plusSeconds(60),false))).toList(); return value(o,new TransactionCost(items,v.declaredTotalUsd(),v.basis())); }),ReasonCode.INVALID_CONVERSION);
    }
    @Test void feeCapAppliesToCompleteDeliveryCost() {
        var original=intent(); var i=original.instruction();
        var limited=new PaymentIntentSnapshot(original.intentId(),1,original.customerId(),original.confirmedBy(),original.confirmedAt(),SettlementAsset.USDC,true,true,
                new PaymentIntentRequest(i.requiredOutcome(),i.deliverBy(),new BigDecimal("5.29"),i.feeCurrency(),i.priority(),Set.of(),true),original.policy());
        var result=service.assess(IntentFixtures.ACTOR,context,limited,fixture(Scenario.NORMAL));
        assertThat(result.status()).isEqualTo(Status.NO_ELIGIBLE_ROUTE);
        assertThat(first(result).findings()).extracting(Finding::code).contains(ReasonCode.FEE_LIMIT_EXCEEDED);
    }
    @Test void unknownAvailabilityIsNotAssumedAvailable() {
        issue(change(SignalType.RAIL_AVAILABILITY,o -> value(o,new RailAvailability(Availability.AVAILABLE,Availability.UNKNOWN,Availability.AVAILABLE,Availability.AVAILABLE))),ReasonCode.UNKNOWN_SIGNAL);
    }
    @Test void degradedAvailabilityIsConservativelyRejected() {
        issue(change(SignalType.RAIL_AVAILABILITY,o -> value(o,new RailAvailability(Availability.AVAILABLE,Availability.AVAILABLE,Availability.DEGRADED,Availability.AVAILABLE))),ReasonCode.RAIL_DEGRADED);
    }
    @Test void reservationsAreSubtractedBeforeReadiness() {
        issue(change(SignalType.LIQUIDITY,o -> { var v=(Liquidity)o.value(); var pockets=v.pockets().stream().map(p -> p.role()!=PocketRole.TREASURY_USDC?p:new Pocket(p.role(),p.asset(),new BigDecimal("20000"),new BigDecimal("15000"),new BigDecimal("5000"))).toList(); return value(o,new Liquidity(pockets)); }),ReasonCode.INSUFFICIENT_USDC);
    }
    @Test void providerFiatNeedsExplicitQuoteAmount() {
        var noQuote=new AssessmentContext(context.paymentId(),context.correlationId(),context.customerId(),context.corridor(),context.sourceAmountUsd(),context.transferAmountUsdc(),context.requiredGasEth(),Map.of(),PaymentRail.AUTO);
        var result=service.assess(IntentFixtures.ACTOR,noQuote,intent(),fixture(Scenario.NORMAL));
        assertThat(first(result).findings()).extracting(Finding::code).contains(ReasonCode.MISSING_PAYOUT_REQUIREMENT);
        assertThat(result.status()).isEqualTo(Status.INSUFFICIENT_EVIDENCE);
    }
    @Test void blockingExternalDataRejectsCandidate() {
        issue(change(SignalType.EXTERNAL_DATA,o -> { var v=(ExternalData)o.value(); return value(o,new ExternalData(v.observations().stream().map(x -> x.kind()!=ExternalKind.CALENDAR_CUTOFF?x:new ExternalObservation(x.kind(),x.unit(),BigDecimal.ONE,ExternalState.BLOCKING,x.sourceId(),x.sourceRecordReference(),x.observedAt(),x.expiresAt())).toList())); }),ReasonCode.EXTERNAL_BLOCKER);
    }
    @Test void excessivePriceDeviationRejectsCandidate() {
        issue(change(SignalType.EXTERNAL_DATA,o -> { var v=(ExternalData)o.value(); return value(o,new ExternalData(v.observations().stream().map(x -> x.kind()!=ExternalKind.STABLECOIN_PRICE_DEVIATION?x:new ExternalObservation(x.kind(),x.unit(),new BigDecimal("101"),x.state(),x.sourceId(),x.sourceRecordReference(),x.observedAt(),x.expiresAt())).toList())); }),ReasonCode.PRICE_DEVIATION_EXCEEDED);
    }
    @Test void actorIsRequired() {
        assertThatThrownBy(() -> service.assess(null,context,intent(),fixture(Scenario.NORMAL))).isInstanceOf(PaymentAccessException.class);
    }
    @Test void evidenceFromAnotherCustomerFailsBeforeReturningResults() {
        var changed=change(SignalType.LATENCY,o -> metadata(o,new Scope(UUID.randomUUID(),context.corridor(),context.sourceAmountUsd()),o.observedAt(),o.receivedAt(),o.expiresAt(),o.unit(),o.quality(),o.network()));
        assertThatThrownBy(() -> assess(changed)).isInstanceOf(PaymentAccessException.class);
    }
    @Test void duplicateGlobalIdsRejectWholeSnapshot() {
        Snapshot s=fixture(Scenario.NORMAL); var list=new ArrayList<>(s.observations()); list.add(list.getFirst());
        assertThatThrownBy(() -> assess(new Snapshot(s.snapshotId(),1,s.candidates(),list))).isInstanceOf(PaymentValidationException.class);
    }
    @Test void walletRouteCannotWinByOmittingRequiredFiatLeg() {
        Snapshot wallet=SimulatedRoutingScenarios.create(Scenario.NORMAL,context,BeneficiaryOutcome.USDC_WALLET,NETWORK,NOW);
        var result=assess(wallet);
        assertThat(result.status()).isEqualTo(Status.NO_ELIGIBLE_ROUTE);
        assertThat(first(result).findings()).extracting(Finding::code).contains(ReasonCode.OUTCOME_NOT_ALLOWED);
    }
    @Test void explicitlyPermittedAlternativeHasSeparateRanking() {
        Snapshot wallet=SimulatedRoutingScenarios.create(Scenario.NORMAL,context,BeneficiaryOutcome.USDC_WALLET,NETWORK,NOW);
        var result=service.assess(IntentFixtures.ACTOR,context,intent(RoutingPriority.LOWEST_COST,BeneficiaryOutcome.SIMULATED_BANK_INR,Set.of(BeneficiaryOutcome.USDC_WALLET)),wallet);
        assertThat(result.status()).isEqualTo(Status.ASSESSED);
        assertThat(result.rankedCandidateIds()).containsOnlyKeys(BeneficiaryOutcome.USDC_WALLET);
    }
    @Test void walletOutcomeStillRequiresUsdcAndEthWithoutFiatPocket() {
        var wallet=SimulatedRoutingScenarios.create(Scenario.NORMAL,context,BeneficiaryOutcome.USDC_WALLET,NETWORK,NOW);
        var result=service.assess(IntentFixtures.ACTOR,context,intent(RoutingPriority.LOWEST_COST,BeneficiaryOutcome.USDC_WALLET,Set.of()),wallet);
        assertThat(result.status()).isEqualTo(Status.ASSESSED);
        assertThat(first(result).totalCostUsd()).isEqualByComparingTo("2.80");
    }
    @Test void fixtureIsAlwaysLabeledSimulatedAndDoesNotMutateAcrossReads() throws Exception {
        Snapshot snapshot=fixture(Scenario.NORMAL);
        assertThat(snapshot.observations()).allMatch(o -> o.evidenceMode()==EvidenceMode.SIMULATED);
        ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
        Snapshot restored=mapper.readValue(mapper.writeValueAsBytes(snapshot),Snapshot.class);
        assertThat(assess(restored)).isEqualTo(assess(snapshot));
        assertThatThrownBy(() -> snapshot.observations().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> assess(snapshot).rankedCandidateIds().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    Snapshot combine(Snapshot first, Snapshot second) {
        String suffix="/"+second.candidates().getFirst().outcome();
        var candidates=new ArrayList<>(first.candidates());
        second.candidates().forEach(c -> candidates.add(new Candidate(c.candidateId()+suffix,c.providerId(),c.rail(),c.outcome(),c.network())));
        var observations=new ArrayList<>(first.observations());
        second.observations().forEach(o -> observations.add(new Observation(o.signalId()+suffix,o.schemaVersion(),o.signalType(),o.candidateId()+suffix,
                o.scope(),o.sourceId(),o.sourceRecordReference(),o.observedAt(),o.receivedAt(),o.expiresAt(),o.unit(),o.evidenceMode(),o.quality(),o.network(),o.value())));
        return new Snapshot(first.snapshotId(),1,candidates,observations);
    }
    @Test void stableTieBreakIsIndependentOfInputOrder() {
        Snapshot s=fixture(Scenario.NORMAL);
        var values=new EnumMap<SignalType,SignalValue>(SignalType.class);
        s.observations().stream().filter(o -> o.candidateId().equals("SIMULATED-A")).forEach(o -> values.put(o.signalType(),o.value()));
        var observations=s.observations().stream().map(o -> o.candidateId().equals("SIMULATED-B")?value(o,values.get(o.signalType())):o).toList();
        var tied=new Snapshot(s.snapshotId(),1,s.candidates(),observations);
        var reversed=new ArrayList<>(s.candidates()); Collections.reverse(reversed);
        assertThat(assess(tied).preferredCandidateId()).isEqualTo("SIMULATED-A");
        assertThat(assess(new Snapshot(s.snapshotId(),1,reversed,observations))).isEqualTo(assess(tied));
    }
    @Test void requiredOutcomeIsNotUndercutByCheaperAlternative() {
        Snapshot wallet=SimulatedRoutingScenarios.create(Scenario.NORMAL,context,BeneficiaryOutcome.USDC_WALLET,NETWORK,NOW);
        var result=service.assess(IntentFixtures.ACTOR,context,intent(RoutingPriority.LOWEST_COST,BeneficiaryOutcome.SIMULATED_BANK_INR,Set.of(BeneficiaryOutcome.USDC_WALLET)),combine(fixture(Scenario.NORMAL),wallet));
        assertThat(result.rankedCandidateIds()).hasSize(2);
        assertThat(result.preferredCandidateId()).isEqualTo("SIMULATED-A");
    }
    @Test void unlikeAlternativeOutcomesRequireExplicitChoice() {
        Snapshot usd=SimulatedRoutingScenarios.create(Scenario.NORMAL,context,BeneficiaryOutcome.SIMULATED_BANK_USD,NETWORK,NOW);
        var result=service.assess(IntentFixtures.ACTOR,context,intent(RoutingPriority.LOWEST_COST,BeneficiaryOutcome.USDC_WALLET,
                Set.of(BeneficiaryOutcome.SIMULATED_BANK_INR,BeneficiaryOutcome.SIMULATED_BANK_USD)),combine(fixture(Scenario.NORMAL),usd));
        assertThat(result.status()).isEqualTo(Status.OUTCOME_CHOICE_REQUIRED);
        assertThat(result.preferredCandidateId()).isNull();
    }
    @Test void policyDisallowedProviderIsExcluded() {
        var original=intent(); var p=original.policy();
        var policy=new CustomerRoutingPolicy("restricted-v2",p.allowedOutcomes(),Set.of("SIMULATED-PROVIDER-B"),p.maximumFeeUsd(),p.requiredApprovals());
        var restricted=new PaymentIntentSnapshot(original.intentId(),1,original.customerId(),original.confirmedBy(),original.confirmedAt(),SettlementAsset.USDC,true,true,original.instruction(),policy);
        var result=service.assess(IntentFixtures.ACTOR,context,restricted,fixture(Scenario.NORMAL));
        assertThat(first(result).findings()).extracting(Finding::code).contains(ReasonCode.PROVIDER_NOT_ALLOWED);
        assertThat(result.preferredCandidateId()).isEqualTo("SIMULATED-B");
    }
    @Test void unsupportedMainnetCandidateIsRejected() {
        Snapshot s=fixture(Scenario.NORMAL); var c=s.candidates().getFirst();
        var candidates=new ArrayList<>(s.candidates()); candidates.set(0,new Candidate(c.candidateId(),c.providerId(),c.rail(),c.outcome(),new Network("base",8453,c.network().tokenContract(),false)));
        issue(new Snapshot(s.snapshotId(),1,candidates,s.observations()),ReasonCode.UNSUPPORTED_NETWORK);
    }
    @Test void liveLabelCannotBeAppliedToSimulatedFeeBudget() {
        issue(change(SignalType.TRANSACTION_COST,o -> new Observation(o.signalId(),1,o.signalType(),o.candidateId(),o.scope(),o.sourceId(),o.sourceRecordReference(),o.observedAt(),o.receivedAt(),o.expiresAt(),o.unit(),EvidenceMode.LIVE,o.quality(),o.network(),o.value())),ReasonCode.UNSUPPORTED_COST_BASIS);
    }
    @Test void assessmentExpiresAtLatestSafeDeliveryStart() {
        var original=intent(); var i=original.instruction();
        var tight=new PaymentIntentSnapshot(original.intentId(),1,original.customerId(),original.confirmedBy(),original.confirmedAt(),SettlementAsset.USDC,true,true,
                new PaymentIntentRequest(i.requiredOutcome(),NOW.plusSeconds(150),i.maximumFee(),i.feeCurrency(),i.priority(),Set.of(),true),original.policy());
        assertThat(service.assess(IntentFixtures.ACTOR,context,tight,fixture(Scenario.NORMAL)).expiresAt()).isEqualTo(NOW.plusSeconds(30));
    }
    @Test void maximumAgeBoundaryIsStale() {
        issue(change(SignalType.LATENCY,o -> metadata(o,o.scope(),NOW.minusSeconds(300),NOW.minusSeconds(299),NOW.plusSeconds(60),o.unit(),o.quality(),o.network())),ReasonCode.STALE_EVIDENCE);
    }
    @Test void farFutureTimestampsAreRejectedWithoutArithmeticOverflow() {
        issue(change(SignalType.LATENCY,o -> metadata(o,o.scope(),Instant.MAX.minusSeconds(1),Instant.MAX.minusSeconds(1),Instant.MAX,o.unit(),o.quality(),o.network())),ReasonCode.FUTURE_EVIDENCE);
    }
    @Test void missingGasPocketNeverBecomesZeroGasRequirement() {
        issue(change(SignalType.LIQUIDITY,o -> value(o,new Liquidity(((Liquidity)o.value()).pockets().stream().filter(p -> p.role()!=PocketRole.GAS_ETH).toList()))),ReasonCode.INVALID_SIGNAL);
    }
    @Test void noCandidateCannotProduceARecommendation() {
        var result=assess(new Snapshot(UUID.randomUUID(),1,List.of(),List.of()));
        assertThat(result.status()).isEqualTo(Status.INSUFFICIENT_EVIDENCE); assertThat(result.preferredCandidateId()).isNull();
    }
    @Test void malformedFeeCurrencyDoesNotEnterRanking() {
        var original=intent(); var i=original.instruction();
        var invalid=new PaymentIntentSnapshot(original.intentId(),1,original.customerId(),original.confirmedBy(),original.confirmedAt(),SettlementAsset.USDC,true,true,
                new PaymentIntentRequest(i.requiredOutcome(),i.deliverBy(),i.maximumFee(),null,i.priority(),Set.of(),true),original.policy());
        assertThatThrownBy(() -> service.assess(IntentFixtures.ACTOR,context,invalid,fixture(Scenario.NORMAL))).isInstanceOf(PaymentValidationException.class);
    }
    @Test void bankProviderAvailabilityCannotClaimLivePayoutEvidence() {
        issue(change(SignalType.RAIL_AVAILABILITY,o -> new Observation(o.signalId(),1,o.signalType(),o.candidateId(),o.scope(),o.sourceId(),o.sourceRecordReference(),o.observedAt(),o.receivedAt(),o.expiresAt(),o.unit(),EvidenceMode.LIVE,o.quality(),o.network(),o.value())),ReasonCode.SIMULATED_PAYOUT_EVIDENCE_REQUIRED);
    }
    @Test void conversionUsesHalfEvenPerLineBeforeSumming() {
        var changed=change(SignalType.TRANSACTION_COST,o -> {
            var v=(TransactionCost)o.value();
            var items=v.items().stream().map(i -> i.component()!=FeeComponent.FX?i:new CostItem(FeeComponent.FX,BigDecimal.ONE,Asset.INR,new BigDecimal("0.00"),
                    new ConversionQuote("SIMULATED-ROUNDING",Asset.INR,new BigDecimal("0.005"),NOW.minusSeconds(5),NOW.plusSeconds(60),true))).toList();
            return value(o,new TransactionCost(items,new BigDecimal("4.80"),v.basis()));
        });
        var candidate=first(assess(changed));
        assertThat(candidate.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(candidate.totalCostUsd()).isEqualByComparingTo("4.80");
    }

}
