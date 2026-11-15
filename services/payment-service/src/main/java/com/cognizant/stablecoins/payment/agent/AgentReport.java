package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.routing.*;
import java.time.Instant;
import java.util.*;
/** Read-only result; source recommendation must still be reviewed through the existing approval APIs. */
public record AgentReport(UUID sourceRecommendationId,int sourceRecommendationVersion,UUID evidenceSnapshotId,
        int intentVersion,String policyVersion,String ruleVersion,String providerModel,String promptVersion,String inferenceProvider,String inferenceRegion,
        Instant createdAt,Instant expiresAt,AgentAnswer answer,List<String> explanation,
        List<AssessmentResult.CandidateAssessment> candidateAssessments,List<ToolTrace> tools,
        List<String> providerResponseIds,long inputTokens,long outputTokens,int modelRequests,java.math.BigDecimal estimatedInferenceCostUsd,String inferenceCostBasis,String evidenceBasis) {
    public AgentReport {explanation=List.copyOf(explanation);candidateAssessments=List.copyOf(candidateAssessments);
        tools=List.copyOf(tools);providerResponseIds=List.copyOf(providerResponseIds);}
    public record ToolTrace(int sequence,String name,String candidateId,int sourceVersion,UUID snapshotId){ }
}
