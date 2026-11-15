package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.routing.RoutingEvidence.SignalType;
import java.util.*;
/** Model chooses grounded explanation topics; application renders numerical facts from validated evidence.
 * No unconstrained narrative, confidence percentage or model-invented financial amount is accepted. */
public record AgentAnswer(Outcome outcome,String selectedCandidateId,List<Explanation> explanations,List<Clarification> clarificationCodes,IntentDraft intentDraft) {
    public AgentAnswer {explanations=List.copyOf(explanations);clarificationCodes=List.copyOf(clarificationCodes);}
    public AgentAnswer(Outcome outcome,String selectedCandidateId,List<Explanation> explanations,List<Clarification> clarificationCodes){this(outcome,selectedCandidateId,explanations,clarificationCodes,null);}
    public enum Outcome { RECOMMENDED, NO_ELIGIBLE_ROUTE, INSUFFICIENT_EVIDENCE, CLARIFICATION_REQUIRED }
    public enum Clarification { AMBIGUOUS_INSTRUCTION, CONFLICTS_WITH_CONFIRMED_INTENT, OUTCOME_CHOICE_REQUIRED }
    public record Explanation(String candidateId,SignalType topic,List<String> signalIds){public Explanation {signalIds=List.copyOf(signalIds);}}
}
