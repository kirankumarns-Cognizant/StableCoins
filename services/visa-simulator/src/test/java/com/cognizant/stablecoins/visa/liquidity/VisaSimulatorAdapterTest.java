package com.cognizant.stablecoins.visa.liquidity;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.visa.liquidity.VisaPaymentAdapter.Currency.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class VisaSimulatorAdapterTest {
    VisaLiquidityPocketRepository pockets=mock(VisaLiquidityPocketRepository.class);
    VisaLiquidityReservationRepository reservations=mock(VisaLiquidityReservationRepository.class);
    VisaLiquidityFundingRepository funding=mock(VisaLiquidityFundingRepository.class);
    VisaLiquidityAuditRepository audit=mock(VisaLiquidityAuditRepository.class);
    Map<String,VisaLiquidityPocket> ps=new HashMap<>();
    Map<UUID,VisaLiquidityReservation> rs=new HashMap<>();
    Map<String,VisaLiquidityFunding> fs=new HashMap<>();
    VisaPaymentAdapter adapter;
    static final String A="SIMULATED-PROVIDER-A",B="SIMULATED-PROVIDER-B";
    @BeforeEach void setup() {
        adapter=new VisaSimulatorAdapter(pockets,reservations,funding,audit,Set.of(A,B),Duration.ofMinutes(5));
        doAnswer(i -> {String id=i.getArgument(0);ps.computeIfAbsent(id,k -> {var p=new VisaLiquidityPocket();p.pocketId=id;
            p.providerId=i.getArgument(1);p.currency=i.getArgument(2);p.total=BigDecimal.ZERO;return p;});return null;}).when(pockets).ensure(any(),any(),any());
        when(pockets.lock(any())).thenAnswer(i -> Optional.ofNullable(ps.get(i.getArgument(0))));
        when(reservations.findByPaymentId(any())).thenAnswer(i -> rs.values().stream().filter(r -> r.paymentId.equals(i.getArgument(0))).findFirst());
        when(reservations.findByPocketId(any())).thenAnswer(i -> rs.values().stream().filter(r -> r.pocketId.equals(i.getArgument(0))).toList());
        when(reservations.save(any())).thenAnswer(i -> {var r=(VisaLiquidityReservation)i.getArgument(0);rs.put(r.reservationId,r);return r;});
        when(reservations.lockReservation(any())).thenAnswer(i -> Optional.ofNullable(rs.get(i.getArgument(0))));
        when(reservations.findPocketLocation(any())).thenAnswer(i -> {
            var r=rs.get(i.getArgument(0));if(r==null)return Optional.empty();
            return Optional.of(new VisaLiquidityReservationRepository.PocketLocation() {
                public String getPocketId(){return r.pocketId;}public String getProviderId(){return r.providerId;}public String getCurrency(){return r.currency;}
            });
        });
        when(funding.findById(any())).thenAnswer(i -> Optional.ofNullable(fs.get(i.getArgument(0))));
        when(funding.save(any())).thenAnswer(i -> {var f=(VisaLiquidityFunding)i.getArgument(0);fs.put(f.operationId,f);return f;});
    }
    VisaPaymentAdapter.Reservation reserve(String payment,String provider, VisaPaymentAdapter.Currency currency,String amount) {
        return adapter.reserve(payment,"CORR-1",provider,currency,new BigDecimal(amount));
    }
    @Test void emptyProviderHasNoInventedPrefundingAndIsAlwaysSimulated() {
        var b=adapter.getBalance(A,USD);assertThat(b.total()).isEqualByComparingTo("0");assertThat(b.evidenceMode()).isEqualTo("SIMULATED");
        assertThatThrownBy(() -> reserve("PAY-1",A,USD,"1")).hasMessage("INSUFFICIENT_SIMULATED_FIAT");
    }
    @Test void prefundIsIdempotentAndMaterialChangesConflict() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));adapter.prefund("FUND-1",A,USD,new BigDecimal("100.00"));
        assertThat(adapter.getBalance(A,USD).total()).isEqualByComparingTo("100");
        assertThatThrownBy(() -> adapter.prefund("FUND-1",B,USD,new BigDecimal("100"))).hasMessage("SIMULATED_PREFUND_OPERATION_CONFLICT");
        assertThatThrownBy(() -> adapter.prefund("FUND-1",A,USD,new BigDecimal("101"))).hasMessage("SIMULATED_PREFUND_OPERATION_CONFLICT");
    }
    @Test void reservationReducesAvailableFiatAndPreventsOverAllocation() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));reserve("PAY-1",A,USD,"70");
        assertThat(adapter.getBalance(A,USD).reserved()).isEqualByComparingTo("70");
        assertThat(adapter.getBalance(A,USD).available()).isEqualByComparingTo("30");
        assertThatThrownBy(() -> reserve("PAY-2",A,USD,"31")).hasMessage("INSUFFICIENT_SIMULATED_FIAT");
    }
    @Test void reservationReplayDoesNotReserveTwice() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));var first=reserve("PAY-1",A,USD,"70");
        assertThat(reserve("PAY-1",A,USD,"70").reservationId()).isEqualTo(first.reservationId());
        assertThat(rs).hasSize(1);verify(audit,times(1)).save(any());
    }
    @Test void differentProvidersCurrenciesAndAmountsCannotReplacePaymentLeg() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));reserve("PAY-1",A,USD,"70");
        assertThatThrownBy(() -> reserve("PAY-1",B,USD,"70")).hasMessage("SIMULATED_PAYOUT_OPERATION_CONFLICT");
        assertThatThrownBy(() -> reserve("PAY-1",A,INR,"70")).hasMessage("SIMULATED_PAYOUT_OPERATION_CONFLICT");
        assertThatThrownBy(() -> reserve("PAY-1",A,USD,"71")).hasMessage("SIMULATED_PAYOUT_OPERATION_CONFLICT");
        assertThatThrownBy(() -> adapter.reserve("PAY-1","CORR-OTHER",A,USD,new BigDecimal("70"))).hasMessage("SIMULATED_PAYOUT_OPERATION_CONFLICT");
    }
    @Test void providerAndCurrencyPocketsAreIndependent() {
        adapter.prefund("FUND-1",A,INR,new BigDecimal("830000"));adapter.prefund("FUND-2",B,USD,new BigDecimal("10000"));
        reserve("PAY-1",A,INR,"830000");
        assertThat(adapter.getBalance(A,INR).available()).isEqualByComparingTo("0");
        assertThat(adapter.getBalance(B,USD).available()).isEqualByComparingTo("10000");
        assertThat(adapter.getBalance(A,USD).available()).isEqualByComparingTo("0");
    }
    @Test void expiredUnsignedReservationReturnsFundsButPaymentCannotBeReused() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));var first=reserve("PAY-1",A,USD,"70");
        rs.get(first.reservationId()).expiresAt=Instant.now().minusSeconds(1);
        assertThat(adapter.getBalance(A,USD).available()).isEqualByComparingTo("100");
        assertThat(adapter.pin(first.reservationId()).status()).isEqualTo("EXPIRED");
        assertThat(reserve("PAY-1",A,USD,"70").status()).isEqualTo("EXPIRED");
    }
    @Test void pinnedReservationNeverExpiresOrCancelsOnTimeout() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));var first=reserve("PAY-1",A,USD,"70");adapter.pin(first.reservationId());
        rs.get(first.reservationId()).expiresAt=Instant.now().minusSeconds(1);
        assertThat(adapter.getBalance(A,USD).available()).isEqualByComparingTo("30");
        assertThatThrownBy(() -> adapter.cancel(first.reservationId())).hasMessage("SIMULATED_PAYOUT_REQUIRES_RESOLUTION");
    }
    @Test void cancelBeforePayoutIsIdempotentAndDoesNotChangeTotal() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));var first=reserve("PAY-1",A,USD,"70");
        adapter.cancel(first.reservationId());adapter.cancel(first.reservationId());
        assertThat(adapter.getBalance(A,USD).total()).isEqualByComparingTo("100");assertThat(adapter.getBalance(A,USD).reserved()).isEqualByComparingTo("0");
    }
    @Test void confirmedSimulatedConsumptionDebitsExactlyOnce() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));var first=reserve("PAY-1",A,USD,"70");adapter.pin(first.reservationId());
        assertThat(adapter.resolve(first.reservationId(),true).status()).isEqualTo("CONSUMED");adapter.resolve(first.reservationId(),true);
        assertThat(adapter.getBalance(A,USD).total()).isEqualByComparingTo("30");assertThat(adapter.getBalance(A,USD).reserved()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> adapter.resolve(first.reservationId(),false)).hasMessage("SIMULATED_PAYOUT_RESOLUTION_CONFLICT");
    }
    @Test void conclusivelyDeclinedSimulatedPayoutReleasesHoldWithoutDebiting() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));var first=reserve("PAY-1",A,USD,"70");adapter.pin(first.reservationId());
        adapter.resolve(first.reservationId(),false);assertThat(adapter.getBalance(A,USD).available()).isEqualByComparingTo("100");
    }
    @Test void cannotConsumeAnUnstartedReservation() {
        adapter.prefund("FUND-1",A,USD,new BigDecimal("100"));var first=reserve("PAY-1",A,USD,"70");
        assertThatThrownBy(() -> adapter.resolve(first.reservationId(),true)).hasMessage("SIMULATED_PAYOUT_RESOLUTION_CONFLICT");
    }
    @Test void noImplicitProviderOrCurrencyFallback() {
        assertThatThrownBy(() -> adapter.getBalance("REAL-VISA",USD)).hasMessage("UNSUPPORTED_SIMULATED_PROVIDER_OR_CURRENCY");
        assertThatThrownBy(() -> adapter.getBalance(A,null)).hasMessage("UNSUPPORTED_SIMULATED_PROVIDER_OR_CURRENCY");
    }
    @Test void fiatPrecisionAndIdentifiersAreValidatedBeforeWriting() {
        for(var n:List.of("0","-1","0.001","100000000000000000000000"))
            assertThatThrownBy(() -> adapter.prefund("FUND-1",A,USD,new BigDecimal(n))).hasMessage("INVALID_SIMULATED_FIAT_AMOUNT");
        assertThatThrownBy(() -> reserve("PAY bad",A,USD,"1")).hasMessage("INVALID_SIMULATOR_IDENTIFIER");verify(funding,never()).save(any());
    }
    @Test void invalidSimulatorConfigurationFailsStartup() {
        assertThatThrownBy(() -> new VisaSimulatorAdapter(pockets,reservations,funding,audit,Set.of(A),Duration.ZERO)).hasMessage("INVALID_SIMULATOR_CONFIGURATION");
        assertThatThrownBy(() -> new VisaSimulatorAdapter(pockets,reservations,funding,audit,Set.of(),Duration.ofMinutes(1))).hasMessage("INVALID_SIMULATOR_CONFIGURATION");
    }
}
