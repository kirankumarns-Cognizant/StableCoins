package com.cognizant.stablecoins.visa.liquidity;

import com.cognizant.stablecoins.flow.SettlementNetwork;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({VisaSimulatorAdapter.class,JdbcWalletPayoutStore.class,VisaWalletConfiguration.class,WalletPayoutPersistenceIT.JsonConfig.class})
class WalletPayoutPersistenceIT {
    @org.springframework.boot.test.context.TestConfiguration
    static class JsonConfig {
        @org.springframework.context.annotation.Bean ObjectMapper walletMapper(){return new ObjectMapper().findAndRegisterModules();}
    }
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    static final String A="SIMULATED-PROVIDER-A";
    static final SettlementNetwork.Token TOKEN=VisaWalletSimulatorTest.TOKEN;
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
        r.add("visa.simulator.wallet.enabled",()->"true");
        r.add("visa.simulator.wallet.scenario",()->"SUCCEEDED");
        r.add("visa.simulator.wallet.tokens-json",()->{
            try{return new ObjectMapper().writeValueAsString(Set.of(TOKEN));}
            catch(Exception e){throw new IllegalStateException(e);}
        });
    }
    @Autowired VisaPaymentAdapter adapter;
    @Autowired JdbcTemplate jdbc;
    @Autowired WalletPayoutStore store;
    @Autowired PlatformTransactionManager tm;
    @BeforeEach void reset(){
        jdbc.execute("truncate visa_wallet_pocket cascade");
        jdbc.execute("truncate visa_transaction cascade");
        jdbc.execute("truncate visa_liquidity_pocket cascade");
        adapter.prefundWallet(UUID.randomUUID(),A,TOKEN,new BigDecimal("10"));
    }
    WalletPayouts.Request request(String payment){
        return new WalletPayouts.Request(UUID.randomUUID(),payment,"CORR-WALLET",A,UUID.randomUUID(),1,
                UUID.randomUUID(),1,TOKEN,"0x"+"d".repeat(40),new BigDecimal("7.000001"));
    }
    @Test void persistedPayoutReplaysAndConsumesOnceWithAudit(){
        var r=request("PAY-1");var p=adapter.createWalletPayout(r);
        assertThat(adapter.createWalletPayout(r).payoutId()).isEqualTo(p.payoutId());
        adapter.finishWalletPayout(p.payoutId());adapter.finishWalletPayout(p.payoutId());
        assertThat(adapter.walletBalance(A,TOKEN).total()).isEqualByComparingTo("2.999999");
        assertThat(adapter.walletBalance(A,TOKEN).reserved()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from visa_wallet_payout_audit",Integer.class)).isEqualTo(2);
        assertThat(adapter.walletPayout(p.payoutId()).transactionHash()).isNull();
    }
    @Test void beforeAndAfterEvidenceSurvivesReplayAndLaterPrefunding(){
        var p=adapter.createWalletPayout(request("PAY-BALANCES"));
        adapter.finishWalletPayout(p.payoutId());adapter.finishWalletPayout(p.payoutId());
        var statement=adapter.walletPayoutStatement(p.payoutId());
        assertThat(statement.changes()).hasSize(2);
        var finalChange=statement.changes().getLast();
        assertThat(finalChange.providerBefore().total()).isEqualByComparingTo("10");
        assertThat(finalChange.providerAfter().total()).isEqualByComparingTo("2.999999");
        assertThat(finalChange.providerAfter().reserved()).isZero();
        assertThat(finalChange.recipientAfter()).isEqualByComparingTo("7.000001");
        assertThat(statement.recipientCreditsInThisSimulator()).isEqualByComparingTo("7.000001");
        adapter.prefundWallet(UUID.randomUUID(),A,TOKEN,BigDecimal.ONE);
        assertThat(adapter.walletPayoutStatement(p.payoutId()).changes()).isEqualTo(statement.changes());
        assertThat(jdbc.queryForObject("select count(*) from visa_wallet_balance_change",Integer.class)).isEqualTo(2);
    }
    @Test void concurrentCreatesCannotOverAllocateTheSameTokenPocket() throws Exception {
        var a=request("PAY-A");var b=request("PAY-B");
        var statuses=parallel(()->adapter.createWalletPayout(a).status().name(),()->adapter.createWalletPayout(b).status().name());
        assertThat(statuses).containsExactlyInAnyOrder("PROCESSING","FAILED");
        assertThat(adapter.walletBalance(A,TOKEN).reserved()).isEqualByComparingTo("7.000001");
    }
    @Test void concurrentCompletionConsumesOnce() throws Exception {
        var p=adapter.createWalletPayout(request("PAY-1"));
        assertThat(parallel(()->adapter.finishWalletPayout(p.payoutId()).status().name(),
                ()->adapter.finishWalletPayout(p.payoutId()).status().name())).containsOnly("SUCCEEDED");
        assertThat(adapter.walletBalance(A,TOKEN).total()).isEqualByComparingTo("2.999999");
    }
    @Test void unknownSurvivesFreshServiceAndRequiresExplicitConclusiveResolution(){
        var engine=new VisaWalletSimulator(store,Set.of(A),Set.of(TOKEN),WalletPayouts.Scenario.TIMEOUT);
        var p=new TransactionTemplate(tm).execute(s->engine.create(request("PAY-UNKNOWN")));
        assertThat(adapter.finishWalletPayout(p.payoutId()).status()).isEqualTo(WalletPayouts.Status.UNKNOWN);
        UUID id=UUID.randomUUID();
        adapter.resolveWalletPayout(p.payoutId(),id,true);adapter.resolveWalletPayout(p.payoutId(),id,true);
        assertThat(adapter.walletBalance(A,TOKEN).total()).isEqualByComparingTo("2.999999");
        assertThatThrownBy(()->adapter.resolveWalletPayout(p.payoutId(),id,false)).hasMessage("WALLET_PAYOUT_RESOLUTION_CONFLICT");
    }
    @Test void auditFailureRollsBackBalanceAndStatus(){
        var p=adapter.createWalletPayout(request("PAY-1"));
        jdbc.execute("alter table visa_wallet_payout_audit add constraint it_no_success check(status<>'SUCCEEDED')");
        try{
            assertThatThrownBy(()->adapter.finishWalletPayout(p.payoutId())).isInstanceOf(RuntimeException.class);
            assertThat(adapter.walletBalance(A,TOKEN).total()).isEqualByComparingTo("10");
            assertThat(adapter.walletBalance(A,TOKEN).reserved()).isEqualByComparingTo("7.000001");
            assertThat(adapter.walletPayout(p.payoutId()).status()).isEqualTo(WalletPayouts.Status.PROCESSING);
        }finally{jdbc.execute("alter table visa_wallet_payout_audit drop constraint it_no_success");}
    }
    @Test void prefundReplayCannotMoveCapacityBetweenTokensOrProviders(){
        UUID id=UUID.randomUUID();
        adapter.prefundWallet(id,A,TOKEN,BigDecimal.ONE);adapter.prefundWallet(id,A,TOKEN,new BigDecimal("1.000000"));
        assertThat(adapter.walletBalance(A,TOKEN).total()).isEqualByComparingTo("11");
        assertThatThrownBy(()->adapter.prefundWallet(id,"SIMULATED-PROVIDER-B",TOKEN,BigDecimal.ONE))
                .hasMessage("SIMULATED_WALLET_FUNDING_CONFLICT");
    }
    VisaPaymentAdapter.PayoutRequest fiat(String payment){
        return new VisaPaymentAdapter.PayoutRequest(UUID.randomUUID(),payment,"CORR-WALLET",A,VisaPaymentAdapter.Currency.USD,
                BigDecimal.ONE,UUID.randomUUID(),1,"0x"+"a".repeat(64),"SUCCEEDED");
    }
    @Test void fiatCannotFollowAWalletProviderPayout(){
        adapter.createWalletPayout(request("PAY-1"));
        assertThatThrownBy(()->adapter.createPayout(fiat("PAY-1"))).hasMessage("SIMULATED_PAYOUT_DESTINATION_CONFLICT");
    }
    @Test void walletCannotFollowAFiatProviderPayout(){
        adapter.prefund("FIAT-FUND",A,VisaPaymentAdapter.Currency.USD,new BigDecimal("10"));
        adapter.createPayout(fiat("PAY-1"));
        assertThatThrownBy(()->adapter.createWalletPayout(request("PAY-1"))).hasMessage("SIMULATED_PAYOUT_DESTINATION_CONFLICT");
    }
    @Test void alteredStoredAmountCannotBeConsumedAsValidProviderEvidence(){
        var p=adapter.createWalletPayout(request("PAY-1"));
        jdbc.update("update visa_wallet_payout set amount=1 where payout_id=?",p.payoutId());
        assertThatThrownBy(()->adapter.finishWalletPayout(p.payoutId())).hasMessage("WALLET_STORED_BINDING_CHANGED");
        assertThat(adapter.walletBalance(A,TOKEN).total()).isEqualByComparingTo("10");
    }
    List<String> parallel(Callable<String> first,Callable<String> second) throws Exception{
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<String> a=()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new AssertionError();return first.call();};
            Callable<String> b=()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new AssertionError();return second.call();};
            var x=pool.submit(a);var y=pool.submit(b);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            return List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));
        }
    }
}