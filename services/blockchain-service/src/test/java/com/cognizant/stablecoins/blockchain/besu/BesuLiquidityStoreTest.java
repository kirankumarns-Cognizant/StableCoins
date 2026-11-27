package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.flow.SettlementNetwork.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** In-memory JDBC interaction fixtures; PostgreSQL serialization is a separate IT gate. */
class BesuLiquidityStoreTest {
    static final Instant NOW=Instant.parse("2026-11-25T10:00:00Z");
    static final Chain CHAIN=new Chain("besu-local",20261125,"0x"+"a".repeat(64),Classification.PRIVATE_DEMO);
    static final Context CONTEXT=context(CHAIN,"b","d");
    static Context context(Chain chain,String contract,String treasury) {
        return new Context(new Token(chain,"0x"+contract.repeat(40),"DEMOUSD",6,"0x"+"c".repeat(64)),
                new NativeAsset(chain,"BESU_GAS",18),"0x"+treasury.repeat(40),FeeModel.EXECUTION_ONLY,"besu-v1");
    }
    static SettlementBinding.Execution execution(Context context,String payment,String quantity,String fee) {
        var i=new SettlementBinding.Instruction(UUID.randomUUID(),1,UUID.randomUUID(),payment,"CORR-"+payment,
                context,"0x"+"e".repeat(40),BigDecimal.TEN,new BigDecimal(quantity),
                SettlementBinding.Outcome.TOKEN_WALLET,BigDecimal.ONE,NOW.plusSeconds(300));
        var r=new SettlementBinding.Review(UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),1,
                "PROVIDER",i,BigDecimal.ONE,new BigDecimal(fee),NOW,NOW.plusSeconds(60));
        return new SettlementBinding.Execution(UUID.randomUUID(),r);
    }
    static SettlementObservation observation(SettlementBinding.Execution e) {
        return new SettlementObservation(e.instruction(),100,BigDecimal.TEN,BigDecimal.ONE,
                new BigDecimal("21000"),new BigDecimal("25000"),new BigDecimal("1000"),
                new BigDecimal("0.000000000025"),NOW,NOW.plusSeconds(15));
    }
    static SettlementPreparation.Evidence evidence(SettlementBinding.Execution e) {
        return new SettlementPreparation.Evidence(new SettlementPreparation.Request(UUID.randomUUID(),e.instruction(),NOW),observation(e));
    }
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final java.util.concurrent.atomic.AtomicReference<Instant> time=new java.util.concurrent.atomic.AtomicReference<>(NOW);
    final Clock clock=new Clock() {
        public java.time.ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(java.time.ZoneId zone){return this;}
        public Instant instant(){return time.get();}
    };
    final SettlementExecutionReads claims=mock(SettlementExecutionReads.class);
    final BesuLiquidityStore store=new BesuLiquidityStore(jdbc,clock,claims);
    final Map<UUID,SettlementBinding.Execution> canonical=new HashMap<>();
    final Map<UUID,Object[]> commitments=new LinkedHashMap<>();
    final Map<UUID,Object[]> reservations=new HashMap<>();
    SettlementBinding.Execution canonical(Context c,String payment,String amount,String fee) {
        var e=execution(c,payment,amount,fee);canonical.put(e.executionId(),e);return e;
    }
    @BeforeEach @SuppressWarnings({"unchecked","rawtypes"}) void setup() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        when(jdbc.queryForList(startsWith("select scope_key"),eq(String.class),any(Object[].class)))
                .thenAnswer(c->List.of(((Object[])c.getRawArguments()[2])[0]));
        doAnswer(c->{
            SettlementBinding.Execution e=c.getArgument(0);
            if (!e.equals(canonical.get(e.executionId())))
                throw new IllegalStateException("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
            return null;
        }).when(claims).requireClaimed(any());
        when(jdbc.query(startsWith("select document::text,fingerprint from besu_execution_reservation"),any(RowMapper.class),any(Object[].class)))
                .thenAnswer(c->{
                    var row=reservations.get(((Object[])c.getRawArguments()[2])[0]);
                    if(row==null)return List.of();
                    var rs=mock(ResultSet.class);when(rs.getString(1)).thenReturn((String)row[1]);
                    when(rs.getString(2)).thenReturn((String)row[2]);
                    return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
                });
        when(jdbc.update(startsWith("insert into besu_execution_reservation"),any(Object[].class)))
                .thenAnswer(c->{Object[] a=(Object[])c.getRawArguments()[1];reservations.put((UUID)a[0],a.clone());return 1;});
        when(jdbc.query(startsWith("select execution_id"),any(RowMapper.class),any(Object[].class)))
                .thenAnswer(c->{
                    String sql=c.getArgument(0);Object arg=((Object[])c.getRawArguments()[2])[0];
                    List<Object> result=new ArrayList<>();
                    for(var row:commitments.values()) {
                        if(!(sql.contains("where execution_id=")?row[0].equals(arg):row[2].equals(arg)&&!"RELEASED".equals(row[6])))continue;
                        var rs=mock(ResultSet.class);when(rs.getObject(1,UUID.class)).thenReturn((UUID)row[0]);
                        for(int n=2;n<=7;n++)when(rs.getString(n)).thenReturn((String)row[n-1]);
                        when(rs.getTimestamp(8)).thenReturn((Timestamp)row[7]);
                        result.add(((RowMapper)c.getArgument(1)).mapRow(rs,0));
                    }
                    return result;
                });
        when(jdbc.update(startsWith("insert into besu_liquidity_commitment"),any(Object[].class)))
                .thenAnswer(c->{
                    Object[] a=(Object[])c.getRawArguments()[1];
                    commitments.put((UUID)a[0],new Object[]{a[0],a[1],a[2],a[3],a[4],a[5],"HELD",a[6]});return 1;
                });
        when(jdbc.update(startsWith("update besu_liquidity_commitment"),any(Object[].class)))
                .thenAnswer(c->{
                    String sql=c.getArgument(0);Object[] a=(Object[])c.getRawArguments()[1];
                    var row=commitments.get(a[a.length-1]);row[6]=sql.contains("'PINNED'")?"PINNED":"RELEASED";return 1;
                });
    }
    @AfterEach void clearTransaction() { TransactionSynchronizationManager.clear(); }
    @Test void allOperationsRequireAnOwnerTransaction() {
        TransactionSynchronizationManager.clear();
        assertThatThrownBy(()->store.lock(CONTEXT)).hasMessage("BESU_LIQUIDITY_TRANSACTION_REQUIRED");
        verifyNoInteractions(jdbc);
    }
    @Test void unsupportedIsolationFailsBeforeAnyStorageAccess() {
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
        assertThatThrownBy(()->store.lock(CONTEXT)).hasMessage("BESU_LIQUIDITY_READ_COMMITTED_REQUIRED");
        verifyNoInteractions(jdbc);
    }
    @Test void exactReplayDoesNotAcquireTwiceOrRefreshExpiredLease() {
        var e=canonical(CONTEXT,"PAY-A","4","0.1");
        store.hold(e,observation(e));time.set(NOW.plusSeconds(120));store.hold(e,observation(e));
        assertThat(commitments).hasSize(1);
        assertThat(commitments.get(e.executionId())[7]).isEqualTo(Timestamp.from(e.review().expiresAt()));
        verify(jdbc,times(1)).update(startsWith("insert into besu_liquidity_commitment"),any(Object[].class));
    }
    @Test void committedTokensAndGasAreSubtractedFromTheObservation() {
        var e=canonical(CONTEXT,"PAY-A","4","0.1");store.hold(e,observation(e));
        var a=store.snapshot(evidence(e));
        assertThat(a.tokenCommitted()).isEqualByComparingTo("4");
        assertThat(a.nativeCommitted()).isEqualByComparingTo("0.1");
        assertThat(a.tokenAvailable()).isEqualByComparingTo("6");
        assertThat(a.nativeAvailable()).isEqualByComparingTo("0.9");
    }
    @Test void anotherTokenSharesGasCommitmentsButNotTokenLiquidity() {
        var e=canonical(CONTEXT,"PAY-A","4","0.1");store.hold(e,observation(e));
        var other=execution(context(CHAIN,"f","d"),"PAY-B","1","0.2");
        var a=store.snapshot(evidence(other));
        assertThat(a.tokenCommitted()).isZero();assertThat(a.nativeCommitted()).isEqualByComparingTo("0.1");
    }
    @Test void genesisAndTreasurySeparateBothPools() {
        var e=canonical(CONTEXT,"PAY-A","4","0.1");store.hold(e,observation(e));
        var reset=new Chain("besu-local",20261125,"0x"+"f".repeat(64),Classification.PRIVATE_DEMO);
        for(var context:List.of(context(reset,"b","d"),context(CHAIN,"b","f"))) {
            var a=store.snapshot(evidence(execution(context,"PAY-B","1","0.1")));
            assertThat(a.tokenCommitted()).isZero();assertThat(a.nativeCommitted()).isZero();
        }
    }
    @Test void pinnedCommitmentsSurviveExpiryAndCannotBeReleasedAsUnsigned() {
        var e=canonical(CONTEXT,"PAY-A","4","0.1");store.hold(e,observation(e));store.pin(e);time.set(NOW.plusSeconds(120));store.pin(e);
        var future=NOW.plusSeconds(120);time.set(future);var next=execution(CONTEXT,"PAY-B","1","0.1");
        var o=new SettlementObservation(next.instruction(),101,BigDecimal.TEN,BigDecimal.ONE,
                BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,future,future.plusSeconds(15));
        var a=store.snapshot(new SettlementPreparation.Evidence(new SettlementPreparation.Request(UUID.randomUUID(),next.instruction(),future),o));
        assertThat(a.tokenCommitted()).isEqualByComparingTo("4");
        assertThat(a.nativeCommitted()).isEqualByComparingTo("0.1");
        assertThatThrownBy(()->store.releaseUnsigned(e)).hasMessage("BESU_PINNED_COMMITMENT_REQUIRES_RECOVERY");
    }
    @Test void expiredUnsignedHoldsCannotBePinned() {
        var e=canonical(CONTEXT,"PAY-A","4","0.1");store.hold(e,observation(e));
        time.set(NOW.plusSeconds(60));
        assertThatThrownBy(()->store.pin(e)).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
    }
    @Test void unsignedReleaseIsIdempotentAndRestoresObservedCapacity() {
        var e=canonical(CONTEXT,"PAY-A","4","0.1");store.hold(e,observation(e));
        store.releaseUnsigned(e);store.releaseUnsigned(e);
        assertThat(store.snapshot(evidence(e)).tokenCommitted()).isZero();
        assertThatThrownBy(()->store.pin(e)).hasMessage("BESU_COMMITMENT_BINDING_CONFLICT");
    }
    @Test void insufficientTokenOrSharedGasCapacityCannotCreateAnotherHold() {
        var e=canonical(CONTEXT,"PAY-A","8","0.9");store.hold(e,observation(e));
        var token=canonical(CONTEXT,"PAY-B","3","0.01");
        assertThatThrownBy(()->store.hold(token,observation(token))).hasMessage("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT");
        var gas=canonical(context(CHAIN,"f","d"),"PAY-C","1","0.2");
        assertThatThrownBy(()->store.hold(gas,observation(gas))).hasMessage("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT");
        assertThat(commitments).hasSize(1);
    }
    @Test void forgedCanonicalOrCorruptStoredMaterialFailsClosed() {
        var e=execution(CONTEXT,"PAY-A","4","0.1");
        assertThatThrownBy(()->store.hold(e,observation(e))).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
        canonical.put(e.executionId(),e);store.hold(e,observation(e));
        commitments.get(e.executionId())[5]="0".repeat(64);
        assertThatThrownBy(()->store.snapshot(evidence(e))).hasMessage("BESU_COMMITMENT_BINDING_CONFLICT");
    }
    @Test void lockWaitCannotReviveAnExpiredObservation() {
        var e=canonical(CONTEXT,"PAY-WAIT","1","0.1");
        when(jdbc.queryForList(startsWith("select scope_key"),eq(String.class),any(Object[].class)))
                .thenAnswer(c->{time.set(NOW.plusSeconds(16));return List.of(CONTEXT.nonceScope());});
        assertThatThrownBy(()->store.hold(e,observation(e))).hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
        assertThat(commitments).isEmpty();
    }
    @Test void expiredUnsignedHoldStopsReducingCapacityButCannotBeRenewed() {
        var e=canonical(CONTEXT,"PAY-EXPIRED","4","0.1");store.hold(e,observation(e));
        time.set(NOW.plusSeconds(60));
        var next=execution(CONTEXT,"PAY-NEXT","1","0.1");
        var o=observation(next);
        var fresh=new SettlementObservation(next.instruction(),101,o.tokenTotal(),o.nativeTotal(),
                o.estimatedGas(),o.gasLimit(),o.gasPriceWei(),o.executionFeeEstimate(),time.get(),time.get().plusSeconds(15));
        var a=store.snapshot(new SettlementPreparation.Evidence(new SettlementPreparation.Request(
                UUID.randomUUID(),next.instruction(),time.get()),fresh));
        assertThat(a.tokenCommitted()).isZero();assertThat(a.nativeCommitted()).isZero();
        store.hold(e,observation(e));
        assertThat(commitments.get(e.executionId())[7]).isEqualTo(Timestamp.from(e.review().expiresAt()));
    }
    @Test void approvedNativeReserveMustCoverFreshGasEstimate() {
        var e=canonical(CONTEXT,"PAY-A","1","0");
        assertThatThrownBy(()->store.hold(e,observation(e))).hasMessage("BESU_APPROVED_GAS_RESERVE_TOO_SMALL");
        assertThat(commitments).isEmpty();
    }
    @Test void pinnedExecutionEvidenceReplaysAfterExpiryWithoutRenewingLease() {
        var e=canonical(CONTEXT,"PAY-PINNED-EVIDENCE","4","0.1");
        store.hold(e,observation(e));store.pin(e);store.capturePinnedObservation(e,observation(e));
        time.set(NOW.plusSeconds(120));
        assertThat(store.pinnedObservation(e)).contains(observation(e));
        assertThat(commitments.get(e.executionId())[7]).isEqualTo(Timestamp.from(e.review().expiresAt()));
    }
    @Test void observationCannotBeRecordedWithoutPinnedCapacity() {
        var e=canonical(CONTEXT,"PAY-NOT-PINNED","4","0.1");
        assertThatThrownBy(()->store.capturePinnedObservation(e,observation(e))).hasMessage("BESU_COMMITMENT_BINDING_CONFLICT");
        store.hold(e,observation(e));
        assertThatThrownBy(()->store.capturePinnedObservation(e,observation(e))).hasMessage("BESU_COMMITMENT_BINDING_CONFLICT");
        assertThat(reservations).isEmpty();
    }
    @Test void corruptPinnedObservationCannotBeReplayed() {
        var e=canonical(CONTEXT,"PAY-CORRUPT-EVIDENCE","4","0.1");
        store.hold(e,observation(e));store.pin(e);store.capturePinnedObservation(e,observation(e));
        reservations.get(e.executionId())[2]="0".repeat(64);
        assertThatThrownBy(()->store.pinnedObservation(e)).hasMessage("BESU_COMMITMENT_BINDING_CONFLICT");
    }
    @Test void partialPinnedStateRequiresRecoveryRatherThanNewAuthority() {
        var e=canonical(CONTEXT,"PAY-PARTIAL-PIN","4","0.1");
        assertThat(store.pinnedObservation(e)).isEmpty();
        store.hold(e,observation(e));store.pin(e);
        assertThatThrownBy(()->store.pinnedObservation(e)).hasMessage("BESU_RESERVATION_REQUIRES_RECOVERY");
    }
    @Test void observationExpiryAtCaptureCannotLeaveVerifiedReservationEvidence() {
        var e=canonical(CONTEXT,"PAY-EXPIRED-EVIDENCE","4","0.1");
        store.hold(e,observation(e));store.pin(e);time.set(NOW.plusSeconds(15));
        assertThatThrownBy(()->store.capturePinnedObservation(e,observation(e))).hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
        assertThat(reservations).isEmpty(); // Full transaction rollback is tested separately in PostgreSQL.
    }
    @Test void anotherInstructionsObservationCannotBeRecordedForPinnedCapacity() {
        var e=canonical(CONTEXT,"PAY-WRONG-EVIDENCE","4","0.1");
        store.hold(e,observation(e));store.pin(e);
        var other=execution(CONTEXT,"PAY-OTHER-EVIDENCE","4","0.1");
        assertThatThrownBy(()->store.capturePinnedObservation(e,observation(other))).hasMessage("SETTLEMENT_OBSERVATION_BINDING_CHANGED");
        assertThat(reservations).isEmpty();
    }
    @Test void signingCapacityIncludesOwnPinnedReservationExactlyOnce() {
        var e=canonical(CONTEXT,"PAY-SIGN-CAPACITY","10","1");
        store.hold(e,observation(e));store.pin(e);store.capturePinnedObservation(e,observation(e));
        store.requireSigningCapacity(e,observation(e));
    }
    @Test void signingCapacityRejectsExternalSpendAgainstAllExistingCommitments() {
        var a=canonical(CONTEXT,"PAY-SIGN-A","5","0.4");
        var b=canonical(CONTEXT,"PAY-SIGN-B","5","0.4");
        store.hold(a,observation(a));store.pin(a);store.capturePinnedObservation(a,observation(a));
        store.hold(b,observation(b));store.pin(b);store.capturePinnedObservation(b,observation(b));
        var o=observation(a);
        var depleted=new SettlementObservation(a.instruction(),100,new BigDecimal("9"),BigDecimal.ONE,
                o.estimatedGas(),o.gasLimit(),o.gasPriceWei(),o.executionFeeEstimate(),NOW,NOW.plusSeconds(15));
        assertThatThrownBy(()->store.requireSigningCapacity(a,depleted)).hasMessage("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT");
        var gasDepleted=new SettlementObservation(a.instruction(),100,BigDecimal.TEN,new BigDecimal("0.7"),
                o.estimatedGas(),o.gasLimit(),o.gasPriceWei(),o.executionFeeEstimate(),NOW,NOW.plusSeconds(15));
        assertThatThrownBy(()->store.requireSigningCapacity(a,gasDepleted)).hasMessage("BESU_AVAILABLE_LIQUIDITY_INSUFFICIENT");
    }
    @Test void signingCapacityRequiresPinnedEvidenceAndApprovedGasCoverage() {
        var e=canonical(CONTEXT,"PAY-SIGN-PIN","1","0.1");
        assertThatThrownBy(()->store.requireSigningCapacity(e,observation(e))).hasMessage("BESU_PINNED_RESERVATION_REQUIRED");
        store.hold(e,observation(e));store.pin(e);store.capturePinnedObservation(e,observation(e));
        var o=observation(e);
        var costly=new SettlementObservation(e.instruction(),100,BigDecimal.TEN,BigDecimal.ONE,
                o.estimatedGas(),o.gasLimit(),new BigDecimal("8000000000000"),new BigDecimal("0.2"),NOW,NOW.plusSeconds(15));
        assertThatThrownBy(()->store.requireSigningCapacity(e,costly)).hasMessage("BESU_APPROVED_GAS_RESERVE_TOO_SMALL");
    }
    @Test void observationBeforeResolvedBlockCannotRecreateSpentCapacity() {
        var e=canonical(CONTEXT,"PAY-STALE-RESOLUTION","1","0.1");
        when(jdbc.queryForObject(startsWith("select max(block_number)"),eq(Long.class),any(Object[].class))).thenReturn(101L);
        assertThatThrownBy(()->store.snapshot(evidence(e))).hasMessage("BESU_BALANCE_PREDATES_RESOLUTION");
        assertThatThrownBy(()->store.hold(e,observation(e))).hasMessage("BESU_BALANCE_PREDATES_RESOLUTION");
        assertThat(commitments).isEmpty();
    }
}
