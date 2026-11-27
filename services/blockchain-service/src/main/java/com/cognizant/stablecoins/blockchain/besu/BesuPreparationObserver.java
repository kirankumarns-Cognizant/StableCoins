package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

/** Canonical owner command -> verified private RPC observation. No signing; availability is not reservation authority. */
@Component
@ConditionalOnProperty(name="app.besu.enabled",havingValue="true")
public class BesuPreparationObserver {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final BesuRuntime runtime;
    private final SettlementPreparationReads reads;
    private final Clock clock;
    private final BesuLiquidityStore liquidity;
    @org.springframework.beans.factory.annotation.Autowired
    public BesuPreparationObserver(JdbcTemplate jdbc,ObjectMapper mapper,BesuRuntime runtime){
        this(jdbc,mapper,runtime,new SettlementPreparationReads(jdbc,mapper),Clock.systemUTC());
    }
    BesuPreparationObserver(JdbcTemplate jdbc,ObjectMapper mapper,BesuRuntime runtime,
            SettlementPreparationReads reads,Clock clock){
        this(jdbc,mapper,runtime,reads,clock,new BesuLiquidityStore(jdbc,clock));
    }
    BesuPreparationObserver(JdbcTemplate jdbc,ObjectMapper mapper,BesuRuntime runtime,
            SettlementPreparationReads reads,Clock clock,BesuLiquidityStore liquidity){
        this.jdbc=jdbc;this.mapper=mapper;this.runtime=runtime;this.reads=reads;this.clock=clock;this.liquidity=liquidity;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void observe(String body){
        var event=FlowJson.read(mapper,body,SettlementPreparation.EVENT);
        var request=SettlementBindingJson.read(event.payload().toString(),SettlementPreparation.Request.class);
        var i=request.instruction();
        if(!event.paymentId().equals(i.paymentId()) || !event.correlationId().equals(i.correlationId()))
            throw new IllegalArgumentException("SETTLEMENT_ENVELOPE_MISMATCH");
        reads.requireRequested(request);
        liquidity.lock(i.context()); // Shared by every token using this chain/treasury gas pool.
        if(existing(request).isPresent())return; // Durable replay is observation only, even after expiry.
        request.requireCurrent(clock.instant());
        var observation=runtime.observe(i);
        var evidence=new SettlementPreparation.Evidence(request,observation);
        evidence.requireFor(request,clock.instant());
        reads.requireRequested(request); // Owner changes while RPC was in flight fail closed.
        jdbc.update("insert into besu_preparation_observation(request_id,request_fingerprint,document,fingerprint) values (?,?,cast(? as jsonb),?) on conflict(request_id) do nothing",
                request.requestId(),SettlementBindingJson.fingerprint(request),
                SettlementBindingJson.write(evidence),SettlementBindingJson.fingerprint(evidence));
        var availability=liquidity.snapshot(evidence);
        availability.requireFor(request,clock.instant());
        jdbc.update("insert into besu_preparation_availability(request_id,document,fingerprint) values (?,cast(? as jsonb),?)",
                request.requestId(),SettlementBindingJson.write(availability),SettlementBindingJson.fingerprint(availability));
        // The treasury scope lock serializes commitment changes and this RPC observation.
        if(existing(request).isEmpty())throw new IllegalStateException("BESU_OBSERVATION_NOT_STORED");
    }
    private Optional<SettlementPreparation.Evidence> existing(SettlementPreparation.Request request){
        return jdbc.query("select request_fingerprint,document::text,fingerprint from besu_preparation_observation where request_id=?",
                (rs,n)->{
                    var value=SettlementBindingJson.read(rs.getString(2),SettlementPreparation.Evidence.class);
                    if(!SettlementBindingJson.fingerprint(request).equals(rs.getString(1))
                            || !request.equals(value.request()) || !SettlementBindingJson.fingerprint(value).equals(rs.getString(3)))
                        throw new IllegalStateException("BESU_PREPARATION_CONFLICT");
                    return value;
                },request.requestId()).stream().findFirst();
    }
}
