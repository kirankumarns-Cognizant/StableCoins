package com.cognizant.stablecoins.visa.liquidity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static com.cognizant.stablecoins.visa.liquidity.VisaPaymentAdapter.Currency.*;
import static org.assertj.core.api.Assertions.*;
/** Requires real PostgreSQL; SIMULATED amounts are never represented as a real bank/Visa balance. */
@DataJpaTest @ActiveProfiles("test") @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true) @Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import(VisaSimulatorAdapter.class)
class ProviderLiquidityPersistenceIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired VisaPaymentAdapter adapter;
    @Autowired VisaLiquidityPocketRepository pockets;
    @Autowired VisaLiquidityReservationRepository reservations;
    @Autowired VisaLiquidityFundingRepository funding;
    @Autowired VisaLiquidityAuditRepository audit;
    @Autowired PlatformTransactionManager tm;
    static final String A="SIMULATED-PROVIDER-A";
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @BeforeEach void reset() {
        new TransactionTemplate(tm).execute(s -> {jdbc.execute("truncate visa_transaction cascade");audit.deleteAll();reservations.deleteAll();funding.deleteAll();pockets.deleteAll();return null;});
        adapter.prefund("FUND-1",A,USD,new BigDecimal("10000"));
    }
    @Test void concurrentPayoutReservationsCannotOverAllocateFiat() throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> reserve("PAY-A",ready,start));var b=pool.submit(() -> reserve("PAY-B",ready,start));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("HELD","INSUFFICIENT_SIMULATED_FIAT");
        }
        assertThat(adapter.getBalance(A,USD).available()).isEqualByComparingTo("3000");assertThat(reservations.count()).isEqualTo(1);
    }
    private String reserve(String payment,CountDownLatch ready,CountDownLatch start) throws Exception {
        ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new AssertionError("start timeout");
        try{return adapter.reserve(payment,"CORR-1",A,USD,new BigDecimal("7000")).status();}
        catch(IllegalStateException e){return e.getMessage();}
    }
    @Test void concurrentCompletionConsumesFiatOnceAndRetainsAudit() throws Exception {
        var r=adapter.reserve("PAY-1","CORR-1",A,USD,new BigDecimal("7000"));adapter.pin(r.reservationId());
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> task=() -> {ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new AssertionError();return adapter.resolve(r.reservationId(),true).status();};
            var a=pool.submit(task);var b=pool.submit(task);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo("CONSUMED");assertThat(b.get(15,TimeUnit.SECONDS)).isEqualTo("CONSUMED");
        }
        assertThat(adapter.getBalance(A,USD).total()).isEqualByComparingTo("3000");assertThat(audit.count()).isEqualTo(3);
    }
    @Test void pinnedFiatHoldSurvivesItsTtlAndCannotBeCancelled() {
        var r=adapter.reserve("PAY-1","CORR-1",A,USD,new BigDecimal("7000"));adapter.pin(r.reservationId());
        new TransactionTemplate(tm).execute(s -> {reservations.findById(r.reservationId()).orElseThrow().expiresAt=Instant.now().minusSeconds(1);return null;});
        assertThat(adapter.getBalance(A,USD).reserved()).isEqualByComparingTo("7000");
        assertThatThrownBy(() -> adapter.cancel(r.reservationId())).hasMessage("SIMULATED_PAYOUT_REQUIRES_RESOLUTION");
        assertThat(reservations.findById(r.reservationId()).orElseThrow().status).isEqualTo("PINNED");
    }
    @Test void simultaneousPrefundReplayAddsFundsOnce() throws Exception {
        var start=new CountDownLatch(1);var ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Void> task=() -> {ready.countDown();start.await(5,TimeUnit.SECONDS);adapter.prefund("FUND-2",A,USD,new BigDecimal("500"));return null;};
            var a=pool.submit(task);var b=pool.submit(task);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
        }
        assertThat(adapter.getBalance(A,USD).total()).isEqualByComparingTo("10500");assertThat(funding.count()).isEqualTo(2);
    }
    VisaPaymentAdapter.PayoutRequest payout(String scenario){return new VisaPaymentAdapter.PayoutRequest(UUID.randomUUID(),"PAY-PAYOUT","CORR-PAYOUT",A,USD,new BigDecimal("100.00"),UUID.randomUUID(),1,"0x"+"a".repeat(64),scenario);}
    @Test void payoutReplayAndCompletionConsumeProviderBalanceOnce(){var req=payout("SUCCEEDED");var a=adapter.createPayout(req);var b=adapter.createPayout(req);assertThat(a.payoutId()).isEqualTo(b.payoutId());assertThat(a.status()).isEqualTo("PROCESSING");adapter.finishPayout(a.payoutId());adapter.finishPayout(a.payoutId());assertThat(adapter.getBalance(A,USD).total()).isEqualByComparingTo("9900");assertThat(adapter.getBalance(A,USD).reserved()).isZero();assertThat(jdbc.queryForObject("select count(*) from visa_transaction",Integer.class)).isEqualTo(1);}
    @Test void timeoutPinsCapacityAndRequiresConclusiveIdempotentResolution(){var p=adapter.createPayout(payout("TIMEOUT"));assertThat(p.status()).isEqualTo("UNKNOWN");assertThat(adapter.finishPayout(p.payoutId()).status()).isEqualTo("UNKNOWN");assertThat(adapter.getBalance(A,USD).reserved()).isEqualByComparingTo("100");UUID resolution=UUID.randomUUID();adapter.resolvePayout(p.payoutId(),resolution,true);adapter.resolvePayout(p.payoutId(),resolution,true);assertThat(adapter.getBalance(A,USD).total()).isEqualByComparingTo("9900");assertThatThrownBy(()->adapter.resolvePayout(p.payoutId(),resolution,false)).hasMessage("PAYOUT_RESOLUTION_CONFLICT");}
    @Test void declinedPayoutIsNotBankCreditAndDoesNotConsumeFiat(){var p=adapter.createPayout(payout("DECLINED"));assertThat(adapter.finishPayout(p.payoutId()).status()).isEqualTo("FAILED");assertThat(adapter.getBalance(A,USD).total()).isEqualByComparingTo("10000");assertThat(adapter.getBalance(A,USD).reserved()).isZero();}
    @Test void changedPayoutReplayConflicts(){var req=payout("SUCCEEDED");adapter.createPayout(req);var changed=new VisaPaymentAdapter.PayoutRequest(req.executionId(),req.paymentId(),req.correlationId(),req.providerId(),req.currency(),new BigDecimal("101.00"),req.quoteId(),req.quoteVersion(),req.transactionHash(),req.scenario());assertThatThrownBy(()->adapter.createPayout(changed)).hasMessage("SIMULATED_PAYOUT_OPERATION_CONFLICT");}
    @Test void payoutAuditFailureRollsBackConsumptionAndStatus(){var p=adapter.createPayout(payout("SUCCEEDED"));jdbc.execute("alter table visa_payout_audit add constraint it_reject_success check(status<>'SUCCEEDED')");try{assertThatThrownBy(()->adapter.finishPayout(p.payoutId())).isInstanceOf(RuntimeException.class);assertThat(adapter.getBalance(A,USD).total()).isEqualByComparingTo("10000");assertThat(adapter.getBalance(A,USD).reserved()).isEqualByComparingTo("100");assertThat(jdbc.queryForObject("select status from visa_transaction where payout_id=?",String.class,p.payoutId())).isEqualTo("PROCESSING");}finally{jdbc.execute("alter table visa_payout_audit drop constraint it_reject_success");}}
    @Test void payoutReusesItsPinnedCapacityEvenWhenNoUnreservedFiatRemains(){
        var request=new VisaPaymentAdapter.PayoutRequest(UUID.randomUUID(),"PAY-PINNED","CORR-PINNED",A,USD,new BigDecimal("10000.00"),UUID.randomUUID(),1,"0x"+"b".repeat(64),"SUCCEEDED");
        var reserved=adapter.reserve(request.paymentId(),request.correlationId(),A,USD,request.amount());adapter.pin(reserved.reservationId());
        assertThat(adapter.getBalance(A,USD).available()).isZero();var payout=adapter.createPayout(request);
        assertThat(payout.status()).isEqualTo("PROCESSING");assertThat(payout.reservationId()).isEqualTo(reserved.reservationId());
        adapter.finishPayout(payout.payoutId());assertThat(adapter.getBalance(A,USD).total()).isZero();assertThat(reservations.count()).isEqualTo(1);
    }

}
