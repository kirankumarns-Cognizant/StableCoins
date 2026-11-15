package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.routing.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.routing.RoutingEvidence.*;
/** Exact allowlist. No payment/customer/URL/SQL arguments and no financial command dependency. */
final class RoutingAgentTools {
    private final AgentEvidenceSource source;private final AuthorizedCustomer actor;private final String paymentId;private final UUID seed;
    private final DeterministicRoutingAssessmentService assessor;private final ObjectMapper mapper;
    AgentEvidenceSource.Material material;AssessmentResult assessment;
    final Set<String> reads=new HashSet<>(),seenSignals=new HashSet<>();final List<AgentReport.ToolTrace> trace=new ArrayList<>();
    RoutingAgentTools(AgentEvidenceSource source,AuthorizedCustomer actor,String paymentId,UUID seed,
            DeterministicRoutingAssessmentService assessor,ObjectMapper mapper){
        this.source=source;this.actor=actor;this.paymentId=paymentId;this.seed=seed;this.assessor=assessor;this.mapper=mapper;
        this.material=source.read(paymentId,seed,actor,false);
    }
    JsonNode call(String name,String arguments){
        try{
            JsonNode args=mapper.readTree(arguments);
            if(args==null||!args.isObject())throw new AgentFailure("AGENT_INVALID_TOOL_ARGUMENTS");
            String candidateId=null;Object result;
            var d=material.document();
            if(name.equals("read_signals")){
                if(args.size()!=2||!args.path("candidateId").isTextual()||!args.path("signals").isArray())throw new AgentFailure("AGENT_INVALID_TOOL_ARGUMENTS");
                candidateId=args.get("candidateId").asText();var candidate=candidate(candidateId);
                var types=EnumSet.noneOf(SignalType.class);for(JsonNode t:args.get("signals"))if(!t.isTextual()||!types.add(SignalType.valueOf(t.asText())))throw new AgentFailure("AGENT_INVALID_TOOL_ARGUMENTS");
                if(types.isEmpty())throw new AgentFailure("AGENT_INVALID_TOOL_ARGUMENTS");
                var views=new ArrayList<Object>();
                for(var o:d.evidence().observations())if(o.candidateId().equals(candidate.candidateId())&&types.contains(o.signalType())){
                    seenSignals.add(o.signalId());
                    // Omit customer/account IDs, wallets, free-form source references and screening reference.
                    JsonNode value=mapper.valueToTree(o.value());
                    if(value instanceof ObjectNode object){object.remove("screeningReference");object.remove("modelVersion");}
                    if(o.value() instanceof ExternalData external){value=mapper.valueToTree(external.observations().stream()
                        .map(e->nullableView("kind",e.kind(),"unit",e.unit(),"magnitude",e.magnitude(),"state",e.state(),"observedAt",e.observedAt(),"expiresAt",e.expiresAt())).toList());}
                    views.add(nullableView("signalId",o.signalId(),"type",o.signalType(),"mode",o.evidenceMode(),"quality",o.quality(),
                        "observedAt",o.observedAt(),"expiresAt",o.expiresAt(),"value",value));
                }
                result=views;
            }else{
                if(args.size()!=0)throw new AgentFailure("AGENT_INVALID_TOOL_ARGUMENTS");
                result=switch(name){
                    case "read_intent" -> Map.of("instruction",d.intent().instruction(),"sourceAmountUsd",d.context().sourceAmountUsd(),
                        "settlementAsset","USDC","testnet",true,"fundingSimulated",true,"fiatPayoutSimulated",d.intent().fiatPayoutSimulated());
                    case "read_policy" -> d.intent().policy();
                    case "list_candidates" -> d.evidence().candidates();
                    case "read_quote" -> d.quote()==null?Map.of("available",false):Map.of("available",true,"simulated",true,"candidateId",d.quote().candidateId(),
                        "providerId",d.quote().providerId(),"transferAmountUsdc",d.quote().transferAmountUsdc(),"payoutAmounts",d.quote().payoutAmounts(),
                        "totalFeeUsd",d.quote().totalFeeUsd(),"expiresAt",d.quote().expiresAt());
                    case "assess_routes" -> {
                        assessment=assessor.assess(actor,d.context(),d.intent(),d.evidence());
                        var view=mapper.<ObjectNode>valueToTree(assessment);view.remove(List.of("paymentId","correlationId","intentId"));
                        for(JsonNode c:view.path("candidates"))((ObjectNode)c).remove("evidence");
                        yield view;
                    }
                    case "refresh_evidence" -> {
                        var next=source.read(paymentId,seed,actor,true);
                        if(!next.equals(material)){material=next;assessment=null;reads.clear();seenSignals.clear();}
                        yield Map.of("sourceVersion",material.version(),"snapshotId",material.document().evidence().snapshotId(),"basis","CAPTURED_SERVER_EVIDENCE_NOT_LIVE_POLL");
                    }
                    default -> throw new AgentFailure("AGENT_TOOL_NOT_ALLOWED");
                };
                reads.add(name);
            }
            trace.add(new AgentReport.ToolTrace(trace.size()+1,name,candidateId,material.version(),material.document().evidence().snapshotId()));
            return mapper.valueToTree(Map.of("sourceVersion",material.version(),"snapshotId",material.document().evidence().snapshotId(),"data",result));
        }catch(AgentFailure e){throw e;}catch(Exception e){throw new AgentFailure("AGENT_INVALID_TOOL_ARGUMENTS");}
    }
    private Map<String,Object> nullableView(Object... pairs){var view=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)view.put((String)pairs[i],pairs[i+1]);return view;}
    Candidate candidate(String id){return material.document().evidence().candidates().stream().filter(c->c.candidateId().equals(id)).findFirst().orElseThrow(()->new AgentFailure("AGENT_UNKNOWN_CANDIDATE"));}
    void verifyCurrent(){if(!material.equals(source.read(paymentId,seed,actor,true)))throw new AgentFailure("AGENT_EVIDENCE_CHANGED");}
}
