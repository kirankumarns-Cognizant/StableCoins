package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.util.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementPreparationTest {
    static final Instant NOW=SettlementBindingTest.NOW;
    static SettlementPreparation.Request request(){
        return new SettlementPreparation.Request(UUID.randomUUID(),SettlementBindingTest.instruction(),NOW);
    }
    @Test void evidenceBindsRequestAndCompleteInstruction(){
        var r=request();
        var evidence=new SettlementPreparation.Evidence(r,SettlementObservationTest.observation(r.instruction()));
        evidence.requireFor(r,NOW);
        var changed=new SettlementPreparation.Request(UUID.randomUUID(),r.instruction(),NOW);
        assertThatThrownBy(()->evidence.requireFor(changed,NOW)).hasMessage("SETTLEMENT_PREPARATION_BINDING_CHANGED");
        assertThatThrownBy(()->new SettlementPreparation.Evidence(request(),evidence.observation()))
                .hasMessage("SETTLEMENT_PREPARATION_BINDING_CHANGED");
    }
    @Test void strictCodecRoundTripsBothDocumentsAndRejectsAddedAuthority() throws Exception {
        var r=request();
        var e=new SettlementPreparation.Evidence(r,SettlementObservationTest.observation(r.instruction()));
        assertThat(SettlementBindingJson.read(SettlementBindingJson.write(r),SettlementPreparation.Request.class)).isEqualTo(r);
        assertThat(SettlementBindingJson.read(SettlementBindingJson.write(e),SettlementPreparation.Evidence.class)).isEqualTo(e);
        var json=(ObjectNode)new ObjectMapper().readTree(SettlementBindingJson.write(e));json.put("executionEnabled",true);
        assertThatThrownBy(()->SettlementBindingJson.read(json.toString(),SettlementPreparation.Evidence.class))
                .hasMessage("INVALID_SETTLEMENT_BINDING");
    }
    @Test void requestsHaveBoundedLifetimeAndEvidenceCannotPredateThem(){
        var r=request();
        for(var time:List.of(NOW.minusNanos(1),NOW.plusSeconds(120)))
            assertThatThrownBy(()->r.requireCurrent(time)).hasMessage("SETTLEMENT_PREPARATION_EXPIRED");
        r.requireCurrent(NOW.plusSeconds(119));
        var later=new SettlementPreparation.Request(r.requestId(),r.instruction(),NOW.plusSeconds(1));
        assertThatThrownBy(()->new SettlementPreparation.Evidence(later,SettlementObservationTest.observation(r.instruction())))
                .hasMessage("SETTLEMENT_PREPARATION_BINDING_CHANGED");
    }
    @Test void freshPreparationCannotReviveAnExpiredRpcObservation(){
        var r=request();var e=new SettlementPreparation.Evidence(r,SettlementObservationTest.observation(r.instruction()));
        assertThatThrownBy(()->e.requireFor(r,NOW.plusSeconds(15))).hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");
    }
    static ObjectNode intent(SettlementBinding.Instruction i) throws Exception {
        var m=new ObjectMapper();
        var root=m.createObjectNode();
        root.put("intentId",i.intentId().toString());root.put("intentVersion",i.intentVersion());
        root.put("customerId",i.customerId().toString());
        var resolved=root.putObject("settlement");
        resolved.set("context",m.readTree(SettlementBindingJson.write(i)).path("context"));
        resolved.put("recipient",i.recipient());resolved.put("tokenAmount",i.tokenAmount());
        var body=root.putObject("instruction");
        body.put("requiredOutcome",i.outcome().name());body.putArray("allowedAlternatives");
        body.put("deliverBy",i.deliverBy().toString());body.put("maximumFee",i.maximumFeeUsd());
        var selected=body.putObject("settlement");
        selected.put("networkId",i.context().token().chain().networkId());
        selected.put("tokenContract",i.context().token().contract());selected.put("tokenAmount",i.tokenAmount());
        return root;
    }
    @Test void currentOwnerSnapshotMustRetainExactAcceptedTerms() throws Exception {
        var i=request().instruction();var reads=new SettlementPreparationReads(mock(JdbcTemplate.class),new ObjectMapper());
        var original=intent(i);
        assertThat(reads.matchesIntent(i,original.toString())).isTrue();
        for(String field:List.of("customerId","intentId","intentVersion","recipient","tokenAmount","genesisHash","maximumFee","deliverBy","requiredOutcome","allowedAlternatives")){
            var changed=original.deepCopy();
            switch(field){
                case "customerId","intentId"->changed.put(field,UUID.randomUUID().toString());
                case "intentVersion"->changed.put(field,2);
                case "recipient"->((ObjectNode)changed.path("settlement")).put(field,"0x"+"f".repeat(40));
                case "tokenAmount"->((ObjectNode)changed.path("instruction").path("settlement")).put(field,999);
                case "genesisHash"->((ObjectNode)changed.path("settlement").path("context").path("token").path("chain")).put(field,"0x"+"f".repeat(64));
                case "maximumFee"->((ObjectNode)changed.path("instruction")).put(field,999);
                case "deliverBy"->((ObjectNode)changed.path("instruction")).put(field,NOW.toString());
                case "requiredOutcome"->((ObjectNode)changed.path("instruction")).put(field,"USDC_WALLET");
                case "allowedAlternatives"->((ArrayNode)changed.path("instruction").path(field)).add("USDC_WALLET");
            }
            assertThat(reads.matchesIntent(i,changed.toString())).as(field).isFalse();
        }
    }
    @Test void missingAndMalformedOwnerSnapshotsFailClosed(){
        var i=request().instruction();var reads=new SettlementPreparationReads(mock(JdbcTemplate.class),new ObjectMapper());
        for(var json:List.of("{}","null","not-json"))assertThat(reads.matchesIntent(i,json)).isFalse();
    }
}
