package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.routing.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.routing.RoutingEvidence.*;
/** Actual model-controlled read-tool loop; deterministic gates own every financial fact and choice. */
@Service
public class RoutingAgent {
    public static final String PROMPT_VERSION="stablecoin-routing-v1";
    private final AgentModel model;private final AgentSettings limits;private final AgentProviderProperties provider;
    private final AgentEvidenceSource source;private final DeterministicRoutingAssessmentService assessor;
    private final ObjectMapper mapper;private final JsonNode schema,toolDefinitions;private final String instructions;
    public RoutingAgent(AgentModel model,AgentSettings limits,AgentProviderProperties provider,AgentEvidenceSource source,
            DeterministicRoutingAssessmentService assessor,ObjectMapper mapper){
        this.model=model;this.limits=limits;this.provider=provider;this.source=source;this.assessor=assessor;
        this.mapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        try{
            schema=this.mapper.readTree(new ClassPathResource("agent/routing-answer-v1.schema.json").getInputStream());
            toolDefinitions=this.mapper.readTree(new ClassPathResource("agent/routing-tools-v1.json").getInputStream());
            instructions=new ClassPathResource("agent/routing-prompt-v1.txt").getContentAsString(StandardCharsets.UTF_8);
        }catch(java.io.IOException e){throw new IllegalStateException("Missing agent contracts",e);}
    }
    public AgentReport run(String paymentId,UUID recommendationId,AuthorizedCustomer actor,String instruction){
        return run(paymentId,recommendationId,actor,instruction,Instant.now().plusMillis(limits.getDeadlineMs()));
    }
    public AgentReport run(String paymentId,UUID recommendationId,AuthorizedCustomer actor,String instruction,Instant deadlineAt){
        if(actor==null)throw new AgentFailure("AGENT_CUSTOMER_NOT_ENABLED");
        limits.require(actor.customerId());provider.validate();
        Duration budget=Duration.between(Instant.now(),deadlineAt);
        if(budget.isNegative()||budget.isZero())throw new AgentFailure("AGENT_DEADLINE_EXCEEDED");
        if(budget.compareTo(Duration.ofMillis(limits.getDeadlineMs()))>0)budget=Duration.ofMillis(limits.getDeadlineMs());
        long started=System.nanoTime();var tools=new RoutingAgentTools(source,actor,paymentId,recommendationId,assessor,mapper);
        var history=mapper.createArrayNode();history.addObject().put("role","user").put("content",AgentInput.redact(instruction));
        long in=0,out=0;int calls=0;var ids=new ArrayList<String>();var usedCalls=new HashSet<String>();String observedModel="";
        for(int round=0;round<limits.getMaximumRounds();round++){
            Duration remaining=budget.minusNanos(System.nanoTime()-started);
            if(remaining.isNegative()||remaining.isZero())throw new AgentFailure("AGENT_DEADLINE_EXCEEDED");
            ObjectNode request=mapper.createObjectNode();request.put("model",provider.getModel()).put("instructions",instructions).put("store",false)
                .put("parallel_tool_calls",false).put("max_output_tokens",limits.getMaximumOutputTokens());
            request.putArray("include").add("reasoning.encrypted_content");request.set("input",history.deepCopy());request.set("tools",toolDefinitions);
            request.set("text",mapper.valueToTree(Map.of("format",Map.of("type","json_schema","name","stablecoin_routing_answer","strict",true,"schema",schema))));
            try{
                int bytes=mapper.writeValueAsBytes(request).length;
                if(bytes>limits.getMaximumRequestBytes())throw new AgentFailure("AGENT_CONTEXT_LIMIT");
                // Conservative preflight allowance; actual usage is recorded, never a claimed dollar cost.
                if(limits.estimatedCost(in+bytes+1024L,out+limits.getMaximumOutputTokens()).compareTo(limits.getMaximumInferenceCostUsd())>0)throw new AgentFailure("AGENT_COST_BUDGET_EXCEEDED");
                if(in+out+bytes+1024L+limits.getMaximumOutputTokens()>limits.getMaximumTotalTokens())throw new AgentFailure("AGENT_TOKEN_BUDGET_EXCEEDED");
            }catch(java.io.IOException e){throw new AgentFailure("AGENT_PROTOCOL_ERROR");}
            var reply=model.exchange(request,remaining);calls++;
            if(reply.inputTokens()<0||reply.outputTokens()<0||reply.model()==null||reply.model().isBlank()
                ||reply.responseId()==null||reply.responseId().isBlank()||!reply.output().isArray())throw new AgentFailure("AGENT_PROTOCOL_ERROR");
            if(reply.outputTokens()>limits.getMaximumOutputTokens()||reply.inputTokens()>limits.getMaximumTotalTokens()
                    ||reply.outputTokens()>limits.getMaximumTotalTokens())throw new AgentFailure("AGENT_TOKEN_BUDGET_EXCEEDED");
            in+=reply.inputTokens();out+=reply.outputTokens();ids.add(reply.responseId());observedModel=reply.model();
            if(limits.estimatedCost(in,out).compareTo(limits.getMaximumInferenceCostUsd())>0)throw new AgentFailure("AGENT_COST_BUDGET_EXCEEDED");
            if(in+out>limits.getMaximumTotalTokens())throw new AgentFailure("AGENT_TOKEN_BUDGET_EXCEEDED");
            if(System.nanoTime()-started>=budget.toNanos())throw new AgentFailure("AGENT_DEADLINE_EXCEEDED");
            var functionCalls=new ArrayList<JsonNode>();var texts=new ArrayList<String>();
            for(JsonNode item:reply.output()){
                switch(item.path("type").asText()){
                    case "function_call" -> functionCalls.add(item);
                    case "reasoning" -> { } // Preserve encrypted reasoning in memory only, never log/persist it.
                    case "message" -> {for(JsonNode c:item.path("content")){
                        if("refusal".equals(c.path("type").asText()))throw new AgentFailure("AGENT_MODEL_REFUSED");
                        if(!"output_text".equals(c.path("type").asText())||!c.path("text").isTextual())throw new AgentFailure("AGENT_PROTOCOL_ERROR");
                        texts.add(c.get("text").asText());
                    }}
                    default -> throw new AgentFailure("AGENT_TOOL_NOT_ALLOWED");
                }
            }
            if(!functionCalls.isEmpty()){
                if(functionCalls.size()!=1||!texts.isEmpty())throw new AgentFailure("AGENT_PROTOCOL_ERROR");
                if(tools.trace.size()>=limits.getMaximumToolCalls())throw new AgentFailure("AGENT_TOOL_LIMIT");
                // Keep original output order, including reasoning items, before tool results.
                reply.output().forEach(item->history.add(item.deepCopy()));
                var call=functionCalls.getFirst();String id=call.path("call_id").asText();
                if(id.isBlank()||!usedCalls.add(id)||!call.path("arguments").isTextual()||!call.path("name").isTextual())throw new AgentFailure("AGENT_PROTOCOL_ERROR");
                JsonNode result=tools.call(call.get("name").asText(),call.get("arguments").asText());
                history.addObject().put("type","function_call_output").put("call_id",id).put("output",result.toString());
                continue;
            }
            if(texts.size()!=1)throw new AgentFailure("AGENT_PROTOCOL_ERROR");
            AgentAnswer answer;
            try{answer=mapper.readValue(texts.getFirst(),AgentAnswer.class);}catch(Exception e){throw new AgentFailure("AGENT_INVALID_OUTPUT");}
            validate(answer,tools,AgentInput.redact(instruction));tools.verifyCurrent();
            var a=tools.assessment;var m=tools.material;
            return new AgentReport(m.recommendationId(),m.version(),m.document().evidence().snapshotId(),m.document().intent().intentVersion(),
                m.document().intent().policy().version(),a.ruleVersion(),observedModel,PROMPT_VERSION,provider.getProvider().name(),provider.getProvider()==AgentProviderProperties.Provider.BEDROCK?provider.getRegion():"PROVIDER_MANAGED",Instant.now(),m.expiresAt(),answer,
                render(answer,a,m.document().evidence()),a.candidates(),tools.trace,ids,in,out,calls,limits.estimatedCost(in,out),"CONFIGURED_TOKEN_RATES_NOT_PROVIDER_BILLING","CAPTURED_SERVER_EVIDENCE_NOT_LIVE_POLL");
        }
        throw new AgentFailure("AGENT_ROUND_LIMIT");
    }
    private void validate(AgentAnswer answer,RoutingAgentTools tools,String instruction){
        if(answer.outcome()==null||answer.explanations().size()>21||answer.clarificationCodes().size()>3)throw new AgentFailure("AGENT_INVALID_OUTPUT");
        if(!tools.reads.containsAll(Set.of("read_intent","read_policy","list_candidates","assess_routes","refresh_evidence"))||tools.assessment==null)
            throw new AgentFailure("AGENT_REQUIRED_EVIDENCE_NOT_READ");
        if(answer.intentDraft()!=null&&answer.intentDraft().validate(instruction,tools.material.document())
                &&(answer.outcome()!=AgentAnswer.Outcome.CLARIFICATION_REQUIRED||!answer.clarificationCodes().contains(AgentAnswer.Clarification.CONFLICTS_WITH_CONFIRMED_INTENT)))
            throw new AgentFailure("AGENT_DRAFT_REQUIRES_CONFIRMATION");
        var assessment=tools.assessment;boolean recommend=answer.outcome()==AgentAnswer.Outcome.RECOMMENDED;
        if(recommend){
            if(assessment.status()!=AssessmentResult.Status.ASSESSED||!Objects.equals(assessment.preferredCandidateId(),answer.selectedCandidateId())
                ||!answer.clarificationCodes().isEmpty())throw new AgentFailure("AGENT_POLICY_SELECTION_REJECTED");
            var q=tools.material.document().quote();
            if(!tools.reads.contains("read_quote")||q==null||!q.expiresAt().isAfter(Instant.now())||!tools.material.expiresAt().isAfter(Instant.now())
                ||!q.candidateId().equals(answer.selectedCandidateId()))throw new AgentFailure("AGENT_QUOTE_OR_EVIDENCE_EXPIRED");
            Set<SignalType> seen=EnumSet.noneOf(SignalType.class);
            tools.material.document().evidence().observations().stream().filter(o->o.candidateId().equals(answer.selectedCandidateId())&&tools.seenSignals.contains(o.signalId()))
                .forEach(o->seen.add(o.signalType()));
            if(seen.size()!=SignalType.values().length)throw new AgentFailure("AGENT_REQUIRED_EVIDENCE_NOT_READ");
        }else{
            if(answer.selectedCandidateId()!=null)throw new AgentFailure("AGENT_POLICY_SELECTION_REJECTED");
            var quote=tools.material.document().quote();
            boolean missingQuote=quote==null||!quote.expiresAt().isAfter(Instant.now())||!tools.material.expiresAt().isAfter(Instant.now());
            if(answer.outcome()==AgentAnswer.Outcome.NO_ELIGIBLE_ROUTE&&assessment.status()!=AssessmentResult.Status.NO_ELIGIBLE_ROUTE
                ||answer.outcome()==AgentAnswer.Outcome.INSUFFICIENT_EVIDENCE&&assessment.status()!=AssessmentResult.Status.INSUFFICIENT_EVIDENCE&&!missingQuote
                ||answer.outcome()==AgentAnswer.Outcome.CLARIFICATION_REQUIRED&&answer.clarificationCodes().isEmpty())throw new AgentFailure("AGENT_STATUS_REJECTED");
        }
        var covered=EnumSet.noneOf(SignalType.class);
        for(var explanation:answer.explanations()){
            tools.candidate(explanation.candidateId());
            if(explanation.topic()==null||explanation.signalIds().isEmpty()||explanation.signalIds().size()>7)throw new AgentFailure("AGENT_UNGROUNDED_EXPLANATION");
            for(String id:explanation.signalIds()){
                boolean known=tools.seenSignals.contains(id)&&tools.material.document().evidence().observations().stream().anyMatch(o->
                    o.signalId().equals(id)&&o.candidateId().equals(explanation.candidateId())&&o.signalType()==explanation.topic());
                if(!known)throw new AgentFailure("AGENT_UNGROUNDED_EXPLANATION");
            }
            if(Objects.equals(explanation.candidateId(),answer.selectedCandidateId()))covered.add(explanation.topic());
        }
        if(recommend&&covered.size()!=SignalType.values().length)throw new AgentFailure("AGENT_UNGROUNDED_EXPLANATION");
    }
    private String signalSummary(SignalValue value){
        if(value instanceof RailAvailability a)return "Network "+a.network()+", RPC "+a.rpc()+", provider "+a.provider()+", beneficiary delivery "+a.beneficiaryDelivery()+".";
        if(value instanceof Liquidity l)return "Available after reservations: "+l.pockets().stream().map(p->p.role()+" "+p.available()+" "+p.asset()).toList()+".";
        if(value instanceof ExternalData d)return "External observations: "+d.observations().stream().map(o->o.kind()+" "+o.state()+" "+o.magnitude()+" "+o.unit()).toList()+".";
        return "Unknown evidence";
    }
    private List<String> render(AgentAnswer answer,AssessmentResult assessment,Snapshot evidence){
        var text=new ArrayList<String>();
        text.add("USDC settlement uses a public testnet; ETH pays gas. Fiat funding, conversion, FX, screening and any bank payout are simulated.");
        if(answer.outcome()==AgentAnswer.Outcome.RECOMMENDED)text.add("Selected "+answer.selectedCandidateId()+" under "+assessment.priority()+" using rule "+assessment.ruleVersion()+".");
        else text.add("Result: "+answer.outcome()+"; no execution is authorized.");
        for(var e:answer.explanations()){
            var a=assessment.candidates().stream().filter(c->c.candidate().candidateId().equals(e.candidateId())).findFirst().orElseThrow();
            String fact=switch(e.topic()){
                case TRANSACTION_COST->"Assessed total delivery fee USD "+a.totalCostUsd()+"; itemized costs and any budgeting conversion remain simulated.";
                case LATENCY->"Estimated end-to-end delivery "+a.deliveryMillis()+" milliseconds.";
                case SUCCESS_RATE->"Observed eligible success ratio "+a.successRate()+"; not a forecast or model confidence.";
                case FRAUD_RISK->"Screening score "+a.fraudScore()+"; simulated screening is labeled in the source evidence.";
                case RAIL_AVAILABILITY,LIQUIDITY,EXTERNAL_DATA -> evidence.observations().stream()
                    .filter(o->o.candidateId().equals(e.candidateId())&&o.signalType()==e.topic()&&e.signalIds().contains(o.signalId()))
                    .map(o->signalSummary(o.value())).reduce((x,y)->x+"; "+y).orElse("Unknown evidence");
            };
            text.add(e.candidateId()+" / "+e.topic()+": "+fact+" Modes: "+a.evidenceModes()+"; eligibility: "+a.eligibility()+"; findings: "+a.findings().stream().filter(f->f.signalType()==e.topic()).map(AssessmentResult.Finding::code).toList()+". Sources: "+e.signalIds());
        }
        if(answer.intentDraft()!=null)text.add("Natural-language intent draft is unconfirmed. Missing fields for a new instruction: "+answer.intentDraft().missingFields()+". Existing confirmed intent remains authoritative.");
        if(!answer.clarificationCodes().isEmpty())text.add("Clarification required: "+answer.clarificationCodes()+". Confirmed intent is unchanged.");
        text.add("Captured evidence only. Human approval and fresh compliance, quote and liquidity checks are required before commitment.");
        return text;
    }
}
