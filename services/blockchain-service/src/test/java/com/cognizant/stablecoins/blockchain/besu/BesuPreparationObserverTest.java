package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.flow.SettlementNetwork.*;

class BesuPreparationObserverTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final BesuLiquidityStore liquidity=mock(BesuLiquidityStore.class);
    final BesuRuntime runtime=mock(BesuRuntime.class);
    final SettlementPreparationReads reads=mock(SettlementPreparationReads.class);
    final Instant now=Instant.parse("2026-11-25T10:00:00Z");
    final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
    final BesuPreparationObserver observer=new BesuPreparationObserver(jdbc,mapper,runtime,reads,clock,liquidity);
    final Chain chain=new Chain("besu-local",20261125,"0x"+"a".repeat(64),Classification.PRIVATE_DEMO);
    final Context context=new Context(new Token(chain,"0x"+"b".repeat(40),"DEMOUSD",6,"0x"+"c".repeat(64)),
            new NativeAsset(chain,"BESU_GAS",18),"0x"+"d".repeat(40),FeeModel.EXECUTION_ONLY,"besu-demo-v1");
    final SettlementBinding.Instruction i=new SettlementBinding.Instruction(UUID.randomUUID(),1,UUID.randomUUID(),
            "PAY-TEST","CORR-TEST",context,"0x"+"e".repeat(40),BigDecimal.TEN,BigDecimal.ONE,
            SettlementBinding.Outcome.TOKEN_WALLET,BigDecimal.ONE,now.plusSeconds(300));
    final SettlementPreparation.Request request=new SettlementPreparation.Request(UUID.randomUUID(),i,now);
    final Map<UUID,Object[]> rows=new HashMap<>();
    SettlementObservation observation(Instant at){
        return new SettlementObservation(i,100,BigDecimal.TEN,BigDecimal.ONE,
                BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,new BigDecimal("0.000000000000000001"),at,at.plusSeconds(15));
    }
    String body(SettlementPreparation.Request request,String payment,String version){
        return FlowJson.json(mapper,new FlowJson.Envelope(UUID.randomUUID().toString(),SettlementPreparation.EVENT,
                version,payment,i.correlationId(),Instant.now(),mapper.valueToTree(request)));
    }
    String body(){return body(request,i.paymentId(),"1.0");}
    @BeforeEach @SuppressWarnings({"rawtypes","unchecked"}) void setup(){
        when(runtime.observe(i)).thenReturn(observation(now));
        when(liquidity.snapshot(any())).thenAnswer(c->new SettlementAvailability(c.getArgument(0),BigDecimal.ZERO,BigDecimal.ZERO,now));
        when(jdbc.query(startsWith("select request_fingerprint"),any(RowMapper.class),any(Object[].class)))
                .thenAnswer(c->{
                    Object[] args=(Object[])c.getRawArguments()[2];var row=rows.get(args[0]);
                    if(row==null)return List.of();
                    var rs=mock(ResultSet.class);
                    when(rs.getString(1)).thenReturn((String)row[1]);
                    when(rs.getString(2)).thenReturn((String)row[2]);
                    when(rs.getString(3)).thenReturn((String)row[3]);
                    return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
                });
        when(jdbc.update(startsWith("insert into besu_preparation_observation"),any(Object[].class)))
                .thenAnswer(c->{
                    Object[] args=(Object[])c.getRawArguments()[1];
                    return rows.putIfAbsent((UUID)args[0],args.clone())==null?1:0;
                });
    }
    @Test void canonicalCommandObservesOnceAndDurableReplayDoesNotCallRpcAgain(){
        observer.observe(body());observer.observe(body());
        verify(runtime).observe(i);
        assertThat(rows).hasSize(1);
        var evidence=SettlementBindingJson.read((String)rows.get(request.requestId())[2],SettlementPreparation.Evidence.class);
        assertThat(evidence.request()).isEqualTo(request);
        verify(liquidity,times(2)).lock(context);
        verify(liquidity).snapshot(evidence);
        verify(jdbc).update(startsWith("insert into besu_preparation_availability"),any(Object[].class));
        verify(reads,times(3)).requireRequested(request);
    }
    @Test void forgedOwnerCommandCannotReachRpc(){
        doThrow(new IllegalStateException("CANONICAL_SETTLEMENT_PREPARATION_REQUIRED")).when(reads).requireRequested(request);
        assertThatThrownBy(()->observer.observe(body())).hasMessage("CANONICAL_SETTLEMENT_PREPARATION_REQUIRED");
        verifyNoInteractions(runtime);assertThat(rows).isEmpty();
    }
    @Test void envelopeAndVersionMustMatch(){
        assertThatThrownBy(()->observer.observe(body(request,"PAY-OTHER","1.0"))).hasMessage("SETTLEMENT_ENVELOPE_MISMATCH");
        assertThatThrownBy(()->observer.observe(body(request,i.paymentId(),"2.0"))).hasMessage("INVALID_FLOW_EVENT");
        verifyNoInteractions(runtime,reads);
    }
    @Test void expiredPendingCommandDoesNotCallRpc(){
        var old=new SettlementPreparation.Request(request.requestId(),i,now.minusSeconds(120));
        assertThatThrownBy(()->observer.observe(body(old,i.paymentId(),"1.0"))).hasMessage("SETTLEMENT_PREPARATION_EXPIRED");
        verifyNoInteractions(runtime);
    }
    @Test void staleRpcResultCannotBePersisted(){
        when(runtime.observe(i)).thenReturn(observation(now.minusSeconds(16)));
        assertThatThrownBy(()->observer.observe(body())).hasMessage("SETTLEMENT_PREPARATION_BINDING_CHANGED");
        assertThat(rows).isEmpty();
    }
    @Test void ownerChangeDuringRpcPreventsPersistence(){
        doNothing().doThrow(new IllegalStateException("SETTLEMENT_OWNER_INSTRUCTION_CHANGED")).when(reads).requireRequested(request);
        assertThatThrownBy(()->observer.observe(body())).hasMessage("SETTLEMENT_OWNER_INSTRUCTION_CHANGED");
        verify(runtime).observe(i);assertThat(rows).isEmpty();
    }
    @Test void corruptDurableEvidenceCannotTriggerAnotherRpcCall(){
        observer.observe(body());rows.get(request.requestId())[3]="0".repeat(64);clearInvocations(runtime);
        assertThatThrownBy(()->observer.observe(body())).hasMessage("BESU_PREPARATION_CONFLICT");
        verifyNoInteractions(runtime);
    }
    @Test void unavailableCommitmentStorageCannotProduceEvidence(){
        doThrow(new IllegalStateException("scope-unavailable")).when(liquidity).lock(context);
        assertThatThrownBy(()->observer.observe(body())).hasMessage("scope-unavailable");
        verifyNoInteractions(runtime);assertThat(rows).isEmpty();
    }
    @Test void unknownPayloadAuthorityIsRejectedBeforeCanonicalRead(){
        var raw=body().replace("\"requestedAt\":","\"executionEnabled\":true,\"requestedAt\":");
        assertThatThrownBy(()->observer.observe(raw)).hasMessage("INVALID_SETTLEMENT_BINDING");
        verifyNoInteractions(reads,runtime);
    }
}
