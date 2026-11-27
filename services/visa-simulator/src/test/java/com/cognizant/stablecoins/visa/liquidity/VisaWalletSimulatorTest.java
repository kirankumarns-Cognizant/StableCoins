package com.cognizant.stablecoins.visa.liquidity;

import com.cognizant.stablecoins.flow.SettlementNetwork;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VisaWalletSimulatorTest {
    static final String PROVIDER = "SIMULATED-PROVIDER-A";
    static final SettlementNetwork.Token TOKEN = new SettlementNetwork.Token(
            new SettlementNetwork.Chain("besu-local",20261125,"0x"+"a".repeat(64),SettlementNetwork.Classification.PRIVATE_DEMO),
            "0x"+"b".repeat(40),"DEMOUSD",6,"0x"+"c".repeat(64));
    static final String RECIPIENT = "0x"+"d".repeat(40);
    final MemoryStore store = new MemoryStore();
    VisaWalletSimulator simulator;
    @BeforeEach void setup() { simulator = simulator(WalletPayouts.Scenario.SUCCEEDED); }
    VisaWalletSimulator simulator(WalletPayouts.Scenario scenario) {
        return new VisaWalletSimulator(store,Set.of(PROVIDER,"SIMULATED-PROVIDER-B"),Set.of(TOKEN),scenario);
    }
    WalletPayouts.Request request() {
        return new WalletPayouts.Request(UUID.randomUUID(),"PAY-WALLET","CORR-WALLET",PROVIDER,
                UUID.randomUUID(),1,UUID.randomUUID(),1,TOKEN,RECIPIENT,new BigDecimal("1.123456"));
    }
    void fund(String amount) { simulator.prefund(UUID.randomUUID(),PROVIDER,TOKEN,new BigDecimal(amount)); }
    @Test void noInventedLiquidityAndInsufficientPayoutCannotBecomeSuccess() {
        var p = simulator.create(request());
        assertThat(p.status()).isEqualTo(WalletPayouts.Status.FAILED);
        fund("10");
        assertThat(simulator.finish(p.payoutId()).status()).isEqualTo(WalletPayouts.Status.FAILED);
        assertThat(simulator.balance(PROVIDER,TOKEN).total()).isEqualByComparingTo("10");
    }
    @Test void prefundingIsSimulatedIdempotentAndRejectsChangedReplay() {
        UUID id=UUID.randomUUID();
        simulator.prefund(id,PROVIDER,TOKEN,new BigDecimal("2.000001"));
        simulator.prefund(id,PROVIDER,TOKEN,new BigDecimal("2.0000010"));
        var b=simulator.balance(PROVIDER,TOKEN);
        assertThat(b.total()).isEqualByComparingTo("2.000001");
        assertThat(b.evidenceMode()).isEqualTo("SIMULATED");
        assertThatThrownBy(()->simulator.prefund(id,PROVIDER,TOKEN,new BigDecimal("3")))
                .hasMessage("SIMULATED_WALLET_FUNDING_CONFLICT");
    }
    @Test void successConsumesExactlyOnceAndNeverClaimsBlockchainEvidence() {
        fund("10"); var r=request(); var p=simulator.create(r);
        assertThat(simulator.create(r)).isEqualTo(p);
        assertThat(simulator.balance(PROVIDER,TOKEN).reserved()).isEqualByComparingTo(r.amount());
        var done=simulator.finish(p.payoutId());
        assertThat(simulator.finish(p.payoutId())).isEqualTo(done);
        assertThat(done.status()).isEqualTo(WalletPayouts.Status.SUCCEEDED);
        assertThat(done.providerReference()).startsWith("SIMULATED-VISA-WALLET-");
        assertThat(done.evidenceMode()).isEqualTo("SIMULATED");
        assertThat(done.blockchainDeliveryVerified()).isFalse();
        assertThat(done.transactionHash()).isNull();
        assertThat(simulator.balance(PROVIDER,TOKEN).total()).isEqualByComparingTo("8.876544");
        assertThat(simulator.balance(PROVIDER,TOKEN).reserved()).isZero();
    }
    @Test void declineReleasesCapacityWithoutDebitingTokens() {
        fund("10");simulator=simulator(WalletPayouts.Scenario.DECLINED);
        var p=simulator.create(request());
        assertThat(simulator.finish(p.payoutId()).status()).isEqualTo(WalletPayouts.Status.FAILED);
        assertThat(simulator.balance(PROVIDER,TOKEN).available()).isEqualByComparingTo("10");
    }
    @Test void unknownNeverAutoCompletesOrReleasesAndResolutionIsIdempotent() {
        fund("10");simulator=simulator(WalletPayouts.Scenario.TIMEOUT);
        var p=simulator.create(request());UUID resolution=UUID.randomUUID();
        assertThat(p.status()).isEqualTo(WalletPayouts.Status.UNKNOWN);
        assertThat(simulator.finish(p.payoutId())).isEqualTo(p);
        assertThat(simulator.balance(PROVIDER,TOKEN).reserved()).isEqualByComparingTo(p.request().amount());
        var done=simulator.resolve(p.payoutId(),resolution,true);
        assertThat(simulator.resolve(p.payoutId(),resolution,true)).isEqualTo(done);
        assertThat(simulator.balance(PROVIDER,TOKEN).total()).isEqualByComparingTo("8.876544");
        assertThatThrownBy(()->simulator.resolve(p.payoutId(),resolution,false)).hasMessage("WALLET_PAYOUT_RESOLUTION_CONFLICT");
        assertThatThrownBy(()->simulator.resolve(p.payoutId(),UUID.randomUUID(),true)).hasMessage("WALLET_PAYOUT_RESOLUTION_CONFLICT");
    }
    @Test void conclusiveFailedTimeoutReleasesOnlySimulatedCapacity() {
        fund("10");simulator=simulator(WalletPayouts.Scenario.TIMEOUT);
        var p=simulator.create(request());simulator.resolve(p.payoutId(),UUID.randomUUID(),false);
        assertThat(simulator.balance(PROVIDER,TOKEN).available()).isEqualByComparingTo("10");
    }
    @Test void aCapturedScenarioCannotBeChangedByConfigurationReplay() {
        fund("10");var r=request();var p=simulator.create(r);
        var other=simulator(WalletPayouts.Scenario.DECLINED);
        assertThat(other.create(r)).isEqualTo(p);
        assertThat(other.finish(p.payoutId()).status()).isEqualTo(WalletPayouts.Status.SUCCEEDED);
    }
    @Test void exactReviewRecipientCorrelationQuantityAndExecutionAreBound() {
        fund("10");var r=request();simulator.create(r);
        List<WalletPayouts.Request> changed=List.of(
            new WalletPayouts.Request(UUID.randomUUID(),r.paymentId(),r.correlationId(),PROVIDER,r.recommendationId(),1,r.quoteId(),1,TOKEN,RECIPIENT,r.amount()),
            new WalletPayouts.Request(r.executionId(),r.paymentId(),"CORR-CHANGED",PROVIDER,r.recommendationId(),1,r.quoteId(),1,TOKEN,RECIPIENT,r.amount()),
            new WalletPayouts.Request(r.executionId(),r.paymentId(),r.correlationId(),PROVIDER,UUID.randomUUID(),1,r.quoteId(),1,TOKEN,RECIPIENT,r.amount()),
            new WalletPayouts.Request(r.executionId(),r.paymentId(),r.correlationId(),PROVIDER,r.recommendationId(),2,r.quoteId(),1,TOKEN,RECIPIENT,r.amount()),
            new WalletPayouts.Request(r.executionId(),r.paymentId(),r.correlationId(),PROVIDER,r.recommendationId(),1,UUID.randomUUID(),1,TOKEN,RECIPIENT,r.amount()),
            new WalletPayouts.Request(r.executionId(),r.paymentId(),r.correlationId(),PROVIDER,r.recommendationId(),1,r.quoteId(),2,TOKEN,RECIPIENT,r.amount()),
            new WalletPayouts.Request(r.executionId(),r.paymentId(),r.correlationId(),PROVIDER,r.recommendationId(),1,r.quoteId(),1,TOKEN,"0x"+"e".repeat(40),r.amount()),
            new WalletPayouts.Request(r.executionId(),r.paymentId(),r.correlationId(),PROVIDER,r.recommendationId(),1,r.quoteId(),1,TOKEN,RECIPIENT,BigDecimal.ONE),
            new WalletPayouts.Request(r.executionId(),r.paymentId(),r.correlationId(),"SIMULATED-PROVIDER-B",r.recommendationId(),1,r.quoteId(),1,TOKEN,RECIPIENT,r.amount()));
        for(var c:changed)assertThatThrownBy(()->simulator.create(c)).hasMessage("SIMULATED_WALLET_PAYOUT_CONFLICT");
        assertThat(store.payouts).hasSize(1);
    }
    @Test void pinnedCapacityPreventsOverAllocation() {
        fund("2");simulator.create(request());
        var r=request();
        var second=new WalletPayouts.Request(UUID.randomUUID(),"PAY-SECOND",r.correlationId(),PROVIDER,r.recommendationId(),1,r.quoteId(),1,TOKEN,RECIPIENT,r.amount());
        assertThat(simulator.create(second).status()).isEqualTo(WalletPayouts.Status.FAILED);
        assertThat(simulator.balance(PROVIDER,TOKEN).reserved()).isEqualByComparingTo("1.123456");
    }
    @Test void actualTokenIdentityNotSymbolControlsSupportedDestination() {
        var changed=new SettlementNetwork.Token(TOKEN.chain(),"0x"+"e".repeat(40),"DEMOUSD",6,TOKEN.codeHash());
        var changedGenesis=new SettlementNetwork.Token(new SettlementNetwork.Chain("besu-local",20261125,"0x"+"f".repeat(64),
                SettlementNetwork.Classification.PRIVATE_DEMO),TOKEN.contract(),"DEMOUSD",6,TOKEN.codeHash());
        for(var t:List.of(changed,changedGenesis))
            assertThatThrownBy(()->simulator.balance(PROVIDER,t)).hasMessage("UNSUPPORTED_SIMULATED_WALLET_DESTINATION");
        assertThatThrownBy(()->simulator.balance("REAL-VISA",TOKEN)).hasMessage("UNSUPPORTED_SIMULATED_WALLET_DESTINATION");
    }
    @Test void baseSepoliaUsdcIsSupportedWhenExplicitlyPinned() {
        var t=new SettlementNetwork.Token(new SettlementNetwork.Chain("base-sepolia",84532,"0x"+"a".repeat(64),
                SettlementNetwork.Classification.PUBLIC_TESTNET),"0x036cbd53842c5426634e7929541ec2318f3dcf7e","USDC",6,"0x"+"b".repeat(64));
        var s=new VisaWalletSimulator(store,Set.of(PROVIDER),Set.of(t),WalletPayouts.Scenario.SUCCEEDED);
        assertThat(s.balance(PROVIDER,t).total()).isZero();
        assertThatThrownBy(()->s.balance(PROVIDER,TOKEN)).hasMessage("UNSUPPORTED_SIMULATED_WALLET_DESTINATION");
    }
    @Test void invalidQuantityAndUnsupportedMainnetFailBeforeStoreWrites() {
        for(String n:List.of("0","-1","0.0000001","10000000000000000000000"))
            assertThatThrownBy(()->simulator.prefund(UUID.randomUUID(),PROVIDER,TOKEN,new BigDecimal(n)))
                    .hasMessage("INVALID_SIMULATED_TOKEN_AMOUNT");
        var mainnet=new SettlementNetwork.Token(new SettlementNetwork.Chain("ethereum",1,"0x"+"a".repeat(64),
                SettlementNetwork.Classification.PUBLIC_TESTNET),TOKEN.contract(),"USDC",6,TOKEN.codeHash());
        assertThatThrownBy(()->new VisaWalletSimulator(store,Set.of(PROVIDER),Set.of(mainnet),WalletPayouts.Scenario.SUCCEEDED))
                .hasMessage("UNSUPPORTED_SIMULATED_WALLET_TOKEN");
        assertThat(store.funding).isEmpty();
    }
    @Test void fiatAndWalletProviderPayoutsCannotShareOnePayment() {
        store.fiatPayments.add("PAY-WALLET");fund("10");
        assertThatThrownBy(()->simulator.create(request())).hasMessage("SIMULATED_PAYOUT_DESTINATION_CONFLICT");
        assertThat(store.payouts).isEmpty();
    }
    @Test void cannotAttachForgedReceiptToSimulatorRecord() {
        var r=request();var now=Instant.now();var id=UUID.randomUUID();
        assertThatThrownBy(()->new WalletPayouts.Payout(id,r,"SIMULATED-VISA-WALLET-"+id,WalletPayouts.Status.SUCCEEDED,
                WalletPayouts.Scenario.SUCCEEDED,null,"LIVE",true,"0x"+"f".repeat(64),now,now))
                .hasMessage("WALLET_SIMULATION_EVIDENCE_REQUIRED");
    }
    @Test void resolvingAnOrdinaryPayoutIsRejectedAndReadsSurviveSimulatorReconstruction() {
        fund("10");var p=simulator.create(request());
        assertThatThrownBy(()->simulator.resolve(p.payoutId(),UUID.randomUUID(),true)).hasMessage("WALLET_PAYOUT_NOT_UNKNOWN");
        assertThat(simulator(WalletPayouts.Scenario.TIMEOUT).get(p.payoutId())).isEqualTo(p);
    }
    @Test void balanceEvidenceShowsReservationsDebitsAndCreditsWithoutDuplicateMovements() {
        fund("20");
        var r=request();
        var five=new WalletPayouts.Request(r.executionId(),r.paymentId(),r.correlationId(),PROVIDER,
                r.recommendationId(),1,r.quoteId(),1,TOKEN,RECIPIENT,new BigDecimal("5"));
        var p=simulator.create(five);
        simulator.create(five);
        var held=simulator.statement(p.payoutId());
        assertThat(held.changes()).hasSize(1);
        var reservation=held.changes().getFirst();
        assertThat(reservation.providerBefore().total()).isEqualByComparingTo("20");
        assertThat(reservation.providerBefore().available()).isEqualByComparingTo("20");
        assertThat(reservation.providerAfter().total()).isEqualByComparingTo("20");
        assertThat(reservation.providerAfter().reserved()).isEqualByComparingTo("5");
        assertThat(reservation.providerAfter().available()).isEqualByComparingTo("15");
        assertThat(held.recipientCreditsInThisSimulator()).isZero();
        simulator.finish(p.payoutId());simulator.finish(p.payoutId());
        var done=simulator.statement(p.payoutId());
        assertThat(done.changes()).hasSize(2);
        var consumed=done.changes().getLast();
        assertThat(consumed.providerBefore().total()).isEqualByComparingTo("20");
        assertThat(consumed.providerAfter().total()).isEqualByComparingTo("15");
        assertThat(consumed.providerAfter().reserved()).isZero();
        assertThat(consumed.providerDebit()).isEqualByComparingTo("5");
        assertThat(consumed.recipientCredit()).isEqualByComparingTo("5");
        assertThat(consumed.recipientBefore()).isZero();
        assertThat(consumed.recipientAfter()).isEqualByComparingTo("5");
        assertThat(done.recipientCreditsInThisSimulator()).isEqualByComparingTo("5");
        assertThat(done.evidenceMode()).isEqualTo("SIMULATED");
        assertThat(done.blockchainDeliveryVerified()).isFalse();
        fund("10");
        assertThat(simulator.statement(p.payoutId()).changes()).isEqualTo(done.changes());
        assertThat(simulator.statement(p.payoutId()).currentProvider().total()).isEqualByComparingTo("25");
    }
    @Test void declinedAndUnresolvedPayoutsNeverCreditRecipient() {
        for(var scenario:List.of(WalletPayouts.Scenario.DECLINED,WalletPayouts.Scenario.TIMEOUT)) {
            var engine=new VisaWalletSimulator(new MemoryStore(),Set.of(PROVIDER),Set.of(TOKEN),scenario);
            engine.prefund(UUID.randomUUID(),PROVIDER,TOKEN,new BigDecimal("10"));
            var p=engine.create(request());engine.finish(p.payoutId());
            var statement=engine.statement(p.payoutId());
            assertThat(statement.recipientCreditsInThisSimulator()).isZero();
            assertThat(statement.changes()).allSatisfy(c->{
                assertThat(c.providerDebit()).isZero();assertThat(c.recipientCredit()).isZero();
            });
            if(scenario==WalletPayouts.Scenario.TIMEOUT) {
                assertThat(statement.currentProvider().reserved()).isEqualByComparingTo("1.123456");
                engine.resolve(p.payoutId(),UUID.randomUUID(),false);
                assertThat(engine.statement(p.payoutId()).currentProvider().reserved()).isZero();
            }
        }
    }
    @Test void recipientCreditsAreScopedToProviderTokenAndNormalizedRecipient() {
        fund("10");var p=simulator.create(request());simulator.finish(p.payoutId());
        assertThat(store.recipientCredits(PROVIDER,TOKEN,RECIPIENT)).isEqualByComparingTo("1.123456");
        assertThat(store.recipientCredits("SIMULATED-PROVIDER-B",TOKEN,RECIPIENT)).isZero();
        assertThat(store.recipientCredits(PROVIDER,TOKEN,"0x"+"e".repeat(40))).isZero();
        var other=new SettlementNetwork.Token(TOKEN.chain(),"0x"+"f".repeat(40),"DEMOUSD",6,TOKEN.codeHash());
        assertThat(store.recipientCredits(PROVIDER,other,RECIPIENT)).isZero();
    }
    static class MemoryStore implements WalletPayoutStore {
        final Map<UUID,List<WalletPayouts.BalanceChange>> changes=new HashMap<>();
        public BigDecimal recipientCredits(String provider,SettlementNetwork.Token token,String recipient){
            return payouts.values().stream().filter(p->p.status()==WalletPayouts.Status.SUCCEEDED
                    && p.request().providerId().equals(provider) && p.request().token().equals(token)
                    && p.request().recipient().equals(recipient)).map(p->p.request().amount()).reduce(BigDecimal.ZERO,BigDecimal::add);
        }
        public void recordBalanceChange(WalletPayouts.BalanceChange change){
            changes.computeIfAbsent(change.payoutId(),id->new ArrayList<>()).add(change);
        }
        public List<WalletPayouts.BalanceChange> balanceChanges(UUID id){return List.copyOf(changes.getOrDefault(id,List.of()));}

        record Pocket(String provider,SettlementNetwork.Token token) {}
        record Funding(Pocket pocket,BigDecimal amount) {}
        final Map<Pocket,BigDecimal> totals=new HashMap<>();
        final Map<UUID,Funding> funding=new HashMap<>();
        final Map<UUID,WalletPayouts.Payout> payouts=new HashMap<>();
        final Set<String> fiatPayments=new HashSet<>();
        public void lockPayment(String id) {}
        public boolean hasFiatPayout(String payment){return fiatPayments.contains(payment);}
        public WalletPayouts.Balance lockBalance(String provider,SettlementNetwork.Token token){
            var pocket=new Pocket(provider,token);
            var reserved=payouts.values().stream().filter(p->p.request().providerId().equals(provider)&&p.request().token().equals(token)
                    && Set.of(WalletPayouts.Status.PROCESSING,WalletPayouts.Status.UNKNOWN).contains(p.status()))
                    .map(p->p.request().amount()).reduce(BigDecimal.ZERO,BigDecimal::add);
            var total=totals.getOrDefault(pocket,BigDecimal.ZERO);
            return new WalletPayouts.Balance(provider,token,total,reserved,total.subtract(reserved),"SIMULATED",Instant.now());
        }
        public Optional<WalletPayouts.Payout> byPayment(String payment){return payouts.values().stream().filter(p->p.request().paymentId().equals(payment)).findFirst();}
        public WalletPayouts.Payout get(UUID id){return Optional.ofNullable(payouts.get(id)).orElseThrow();}
        public void prefund(UUID id,String provider,SettlementNetwork.Token token,BigDecimal amount){
            var value=new Funding(new Pocket(provider,token),amount);var prior=funding.get(id);
            if(prior!=null){if(!prior.equals(value))throw new IllegalStateException("SIMULATED_WALLET_FUNDING_CONFLICT");return;}
            funding.put(id,value);totals.merge(value.pocket(),amount,BigDecimal::add);
        }
        public void insert(WalletPayouts.Payout p){payouts.put(p.payoutId(),p);}
        public void transition(WalletPayouts.Payout p,boolean consume){
            if(consume)totals.compute(new Pocket(p.request().providerId(),p.request().token()),(k,v)->v.subtract(p.request().amount()));
            payouts.put(p.payoutId(),p);
        }
    }
}