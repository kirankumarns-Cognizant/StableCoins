package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.routing.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.domain.*;
import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import com.cognizant.stablecoins.payment.support.IntentFixtures;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.routing.RoutingEvidence.*;
final class AgentFixtures {
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static final Instant NOW=Instant.now();
    static final AuthorizedCustomer ACTOR=IntentFixtures.ACTOR;
    static final UUID SEED=UUID.randomUUID();
    static final Network NETWORK=new Network("base-sepolia",84532,"0x036CbD53842c5426634e7929541eC2318f3dCF7e",true);
    static final AssessmentRules RULES=new AssessmentRules("rules-v1",Duration.ofMinutes(5),Duration.ofHours(24),100,95,
        new BigDecimal("0.95"),new BigDecimal("30"),new BigDecimal("100"),20,200);
    static final DeterministicRoutingAssessmentService ASSESSOR=new DeterministicRoutingAssessmentService(RULES,NETWORK,Clock.fixed(NOW,ZoneOffset.UTC));
    static AgentSettings settings(){var s=new AgentSettings();s.setEnabled(true);s.setAllowedCustomerIds(Set.of(ACTOR.customerId()));
        s.setInputUsdPerMillion(new BigDecimal("0.1"));s.setOutputUsdPerMillion(new BigDecimal("0.2"));s.setMaximumRounds(20);return s;}
    static AgentProviderProperties provider(){var p=new AgentProviderProperties();p.setModel("test-model-not-live");p.setApiKeySecretName("test-only-agent-secret");return p;}
    static AgentEvidenceSource.Material material(SimulatedRoutingScenarios.Scenario scenario,int version){
        var c=new AssessmentContext("PAY-AGENT","CORR-AGENT",ACTOR.customerId(),"US-IN",new BigDecimal("10000"),new BigDecimal("10000"),
            new BigDecimal("0.0002"),Map.of(BeneficiaryOutcome.SIMULATED_BANK_INR,new BigDecimal("830000")),PaymentRail.AUTO);
        var i=new PaymentIntentSnapshot(UUID.nameUUIDFromBytes("intent".getBytes(java.nio.charset.StandardCharsets.UTF_8)),1,ACTOR.customerId(),ACTOR.actorId(),NOW.minusSeconds(10),SettlementAsset.USDC,true,true,
            new PaymentIntentRequest(BeneficiaryOutcome.SIMULATED_BANK_INR,NOW.plusSeconds(300),new BigDecimal("25"),SupportedCurrency.USD,RoutingPriority.LOWEST_COST,Set.of(),true),IntentFixtures.policy("test-v1"));
        var e=SimulatedRoutingScenarios.create(scenario,c,BeneficiaryOutcome.SIMULATED_BANK_INR,NETWORK,NOW);
        var a=ASSESSOR.assess(ACTOR,c,i,e);String candidate=a.preferredCandidateId()==null?"SIMULATED-A":a.preferredCandidateId();
        var q=new RecommendationDocument.Quote(UUID.randomUUID(),version,c.paymentId(),c.correlationId(),c.customerId(),candidate,"SIMULATED-PROVIDER-"+candidate.substring(candidate.length()-1),c.sourceAmountUsd(),c.transferAmountUsdc(),c.requiredPayoutAmounts(),new BigDecimal(candidate.endsWith("A")?"5.30":"8.30"),NOW.minusSeconds(5),NOW.plusSeconds(100),true);
        var d=new RecommendationDocument(i,c,e,a,q,new RecommendationDocument.InstructionBinding(UUID.randomUUID(),UUID.randomUUID(),"0x"+"1".repeat(40),"INR"),"Deterministic captured evidence","deterministic-v1");
        return new AgentEvidenceSource.Material(SEED,version,NOW.plusSeconds(100),d);
    }
    static ObjectNode tool(String name,int id){return tool(name,id,"{}");}
    static ObjectNode tool(String name,int id,String args){return JSON.createObjectNode().put("type","function_call").put("name",name).put("call_id","call_"+id).put("arguments",args);}
    static AgentAnswer answer(AgentEvidenceSource.Material m,String selected){
        var refs=m.document().evidence().observations().stream().filter(o->o.candidateId().equals(selected))
            .map(o->new AgentAnswer.Explanation(selected,o.signalType(),List.of(o.signalId()))).toList();
        return new AgentAnswer(AgentAnswer.Outcome.RECOMMENDED,selected,refs,List.of());
    }
    static ObjectNode message(Object answer){var m=JSON.createObjectNode().put("type","message");m.putArray("content").addObject().put("type","output_text").put("text",JSON.valueToTree(answer).toString());return m;}
}
