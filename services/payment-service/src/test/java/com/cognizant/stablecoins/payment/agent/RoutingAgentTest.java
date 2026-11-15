package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.routing.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static com.cognizant.stablecoins.payment.routing.SimulatedRoutingScenarios.Scenario.*;
import static org.assertj.core.api.Assertions.*;
class RoutingAgentTest {
    final List<ObjectNode> requests=new ArrayList<>();
    AgentSettings limits=settings();AgentEvidenceSource.Material material=material(NORMAL,1);
    AgentEvidenceSource source=(p,r,a,latest)->material;
    final List<JsonNode> script=new ArrayList<>();
    void gather(String candidate){for(String n:List.of("read_intent","read_policy","list_candidates","read_quote","assess_routes"))script.add(tool(n,script.size()));
        script.add(tool("read_signals",script.size(),"{\"candidateId\":\""+candidate+"\",\"signals\":[\"SUCCESS_RATE\",\"LATENCY\",\"TRANSACTION_COST\",\"FRAUD_RISK\",\"RAIL_AVAILABILITY\",\"LIQUIDITY\",\"EXTERNAL_DATA\"]}"));}
    RoutingAgent agent(){AtomicInteger i=new AtomicInteger();return new RoutingAgent((request,remaining)->{
        requests.add(request.deepCopy());int index=i.getAndIncrement();if(index>=script.size())throw new AssertionError("Unexpected model request");
        return new AgentModel.Reply(JSON.createArrayNode().add(script.get(index)),20,10,"resp_"+index,"test-model-snapshot");
    },limits,provider(),source,ASSESSOR,JSON);}
    AgentReport run(){return agent().run("PAY-AGENT",SEED,ACTOR,"Pay the confirmed supplier; explain the seven signals.");}
    void complete(String selected){gather(selected);script.add(tool("refresh_evidence",script.size()));script.add(message(answer(material,selected)));}
    void rejects(String code){assertThatThrownBy(this::run).isInstanceOf(AgentFailure.class).hasMessage(code);}
    @Test void modelControlsReadLoopAndProducesGroundedReviewableReport(){complete("SIMULATED-A");var report=run();
        assertThat(report.answer().selectedCandidateId()).isEqualTo("SIMULATED-A");assertThat(report.tools()).hasSize(7);
        assertThat(report.providerModel()).isEqualTo("test-model-snapshot");assertThat(report.promptVersion()).isEqualTo(RoutingAgent.PROMPT_VERSION);
        assertThat(report.explanation().toString()).contains("ETH pays gas","simulated","Captured evidence only");
        assertThat(report.inputTokens()).isEqualTo(160);assertThat(report.estimatedInferenceCostUsd()).isPositive();
        assertThat(requests).allSatisfy(r->{assertThat(r.path("store").asBoolean()).isFalse();assertThat(r.path("parallel_tool_calls").asBoolean()).isFalse();
            assertThat(r.path("text").path("format").path("strict").asBoolean()).isTrue();});
        String sent=requests.toString();assertThat(sent).doesNotContain(ACTOR.customerId().toString(),ACTOR.actorId(),material.document().instruction().recipientWallet(),"screeningReference");
    }
    @Test void refreshedProviderOutageRequiresNewReadsAndRevisesToB(){
        var original=material;var changed=material(PROVIDER_OUTAGE,2);AtomicInteger refreshes=new AtomicInteger();
        source=(p,r,a,latest)->latest&&refreshes.incrementAndGet()>=1?changed:original;
        gather("SIMULATED-A");script.add(tool("refresh_evidence",script.size()));gather("SIMULATED-B");script.add(message(answer(changed,"SIMULATED-B")));
        var report=run();assertThat(report.answer().selectedCandidateId()).isEqualTo("SIMULATED-B");assertThat(report.sourceRecommendationVersion()).isEqualTo(2);
        assertThat(report.evidenceSnapshotId()).isEqualTo(changed.document().evidence().snapshotId());
    }
    @ParameterizedTest @ValueSource(strings={"approve_payment","reserve_funds","submit_transaction","sign","http_get","execute_sql"})
    void financialAndArbitraryToolsAreRejected(String name){script.add(tool(name,1));rejects("AGENT_TOOL_NOT_ALLOWED");}
    @Test void modelCannotChooseHigherCostCandidateAgainstPriority(){complete("SIMULATED-B");rejects("AGENT_POLICY_SELECTION_REJECTED");}
    @Test void fabricatedSourceReferenceIsRejected(){gather("SIMULATED-A");script.add(tool("refresh_evidence",script.size()));var a=answer(material,"SIMULATED-A");
        var refs=new ArrayList<>(a.explanations());var old=refs.getFirst();refs.set(0,new AgentAnswer.Explanation(old.candidateId(),old.topic(),List.of("invented")));
        script.add(message(new AgentAnswer(a.outcome(),a.selectedCandidateId(),refs,List.of())));rejects("AGENT_UNGROUNDED_EXPLANATION");}
    @Test void extraModelConfidenceFieldIsRejected(){gather("SIMULATED-A");script.add(tool("refresh_evidence",script.size()));var output=JSON.<ObjectNode>valueToTree(answer(material,"SIMULATED-A"));output.put("confidence",0.99);script.add(message(output));rejects("AGENT_INVALID_OUTPUT");}
    @Test void selectedSignalsMustActuallyBeRead(){for(String n:List.of("read_intent","read_policy","list_candidates","read_quote","assess_routes","refresh_evidence"))script.add(tool(n,script.size()));
        script.add(message(answer(material,"SIMULATED-A")));rejects("AGENT_REQUIRED_EVIDENCE_NOT_READ");}
    @Test void expiredQuoteCanReturnInsufficientEvidence(){
        var d=material.document();var old=d.quote();
        var expired=new com.cognizant.stablecoins.payment.approval.RecommendationDocument.Quote(old.quoteId(),old.version(),old.paymentId(),old.correlationId(),old.customerId(),old.candidateId(),old.providerId(),old.sourceAmountUsd(),old.transferAmountUsdc(),old.payoutAmounts(),old.totalFeeUsd(),Instant.now().minusSeconds(60),Instant.now().minusSeconds(1),true);
        material=new AgentEvidenceSource.Material(material.recommendationId(),material.version(),material.expiresAt(),new com.cognizant.stablecoins.payment.approval.RecommendationDocument(d.intent(),d.context(),d.evidence(),d.assessment(),expired,d.instruction(),d.rationale(),d.producerVersion()));
        gather("SIMULATED-A");script.add(tool("refresh_evidence",script.size()));script.add(message(new AgentAnswer(AgentAnswer.Outcome.INSUFFICIENT_EVIDENCE,null,List.of(),List.of())));
        assertThat(run().answer().outcome()).isEqualTo(AgentAnswer.Outcome.INSUFFICIENT_EVIDENCE);
    }
    @Test void absoluteDeadlineBlocksInferenceBeforeReadingTools(){
        assertThatThrownBy(()->agent().run("PAY-AGENT",SEED,ACTOR,"review",Instant.now().minusSeconds(1))).hasMessage("AGENT_DEADLINE_EXCEEDED");assertThat(requests).isEmpty();
    }
    @Test void draftedChangeCannotSilentlyUseConfirmedPayment(){
        String text="USD 12000";var draft=new IntentDraft("12000",null,null,null,null,List.of(),false,List.of(new IntentDraft.Span(IntentDraft.Field.SOURCE_AMOUNT_USD,0,text.length())));
        gather("SIMULATED-A");script.add(tool("refresh_evidence",script.size()));var a=answer(material,"SIMULATED-A");
        script.add(message(new AgentAnswer(a.outcome(),a.selectedCandidateId(),a.explanations(),List.of(),draft)));
        assertThatThrownBy(()->agent().run("PAY-AGENT",SEED,ACTOR,text)).hasMessage("AGENT_DRAFT_REQUIRES_CONFIRMATION");
    }
    @Test void missingRefreshIsRejected(){gather("SIMULATED-A");script.add(message(answer(material,"SIMULATED-A")));rejects("AGENT_REQUIRED_EVIDENCE_NOT_READ");}
    @Test void sourceChangedAfterFinalModelReplyIsRejected(){complete("SIMULATED-A");var changed=material(PROVIDER_OUTAGE,2);AtomicInteger reads=new AtomicInteger();
        source=(p,r,a,latest)->reads.incrementAndGet()>2?changed:material;rejects("AGENT_EVIDENCE_CHANGED");}
    @Test void missingEvidenceCanReturnIncompleteWithoutGuessing(){material=material(NO_ELIGIBLE_ROUTE,1);gather("SIMULATED-A");script.add(tool("refresh_evidence",script.size()));
        script.add(message(new AgentAnswer(AgentAnswer.Outcome.NO_ELIGIBLE_ROUTE,null,List.of(),List.of())));assertThat(run().answer().selectedCandidateId()).isNull();}
    @Test void intentContradictionCanRequireClarificationWithoutMutation(){gather("SIMULATED-A");script.add(tool("refresh_evidence",script.size()));
        script.add(message(new AgentAnswer(AgentAnswer.Outcome.CLARIFICATION_REQUIRED,null,List.of(),List.of(AgentAnswer.Clarification.CONFLICTS_WITH_CONFIRMED_INTENT))));
        assertThat(run().answer().outcome()).isEqualTo(AgentAnswer.Outcome.CLARIFICATION_REQUIRED);assertThat(material.document().intent().instruction().confirmed()).isTrue();}
    @Test void crossPaymentArgumentsCannotExpandToolScope(){script.add(tool("read_intent",1,"{\"paymentId\":\"PAY-OTHER\"}"));rejects("AGENT_INVALID_TOOL_ARGUMENTS");}
    @Test void unknownCandidateCannotBeInspected(){script.add(tool("read_signals",1,"{\"candidateId\":\"OTHER\",\"signals\":[\"LIQUIDITY\"]}"));rejects("AGENT_UNKNOWN_CANDIDATE");}
    @Test void disabledAgentDoesNotCallProvider(){limits.setEnabled(false);rejects("AGENT_DISABLED");assertThat(requests).isEmpty();}
    @Test void unconfiguredCustomerDoesNotCallProvider(){limits.setAllowedCustomerIds(Set.of());rejects("AGENT_CUSTOMER_NOT_ENABLED");assertThat(requests).isEmpty();}
    @Test void roundLimitIsEnforced(){limits.setMaximumRounds(1);script.add(tool("read_intent",1));rejects("AGENT_ROUND_LIMIT");}
    @Test void toolLimitIsEnforced(){limits.setMaximumToolCalls(1);script.add(tool("read_intent",1));script.add(tool("read_policy",2));rejects("AGENT_TOOL_LIMIT");}
    @Test void costBudgetRejectsBeforeProviderCall(){limits.setMaximumInferenceCostUsd(new java.math.BigDecimal("0.000001"));rejects("AGENT_COST_BUDGET_EXCEEDED");assertThat(requests).isEmpty();}
    @Test void repeatedToolCallIdIsRejected(){script.add(tool("read_intent",1));script.add(tool("read_policy",1));rejects("AGENT_PROTOCOL_ERROR");}
    @Test void reasoningItemsStayInOriginalOrderOnlyWithinRunMemory(){
        AtomicInteger n=new AtomicInteger();RoutingAgent a=new RoutingAgent((request,remaining)->{requests.add(request.deepCopy());int i=n.getAndIncrement();
            if(i==1)throw new AgentFailure("STOP_TEST");var items=JSON.createArrayNode();items.addObject().put("type","reasoning").put("encrypted_content","encrypted-test");items.add(tool("read_intent",1));
            return new AgentModel.Reply(items,1,1,"resp1","model");
        },limits,provider(),source,ASSESSOR,JSON);
        assertThatThrownBy(()->a.run("PAY-AGENT",SEED,ACTOR,"review")).hasMessage("STOP_TEST");
        var history=requests.get(1).path("input");assertThat(history.get(1).path("type").asText()).isEqualTo("reasoning");
        assertThat(history.get(2).path("type").asText()).isEqualTo("function_call");assertThat(history.get(3).path("type").asText()).isEqualTo("function_call_output");
    }
}
