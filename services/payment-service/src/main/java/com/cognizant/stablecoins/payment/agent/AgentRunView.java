package com.cognizant.stablecoins.payment.agent;
import java.time.Instant;
import java.util.UUID;
public record AgentRunView(UUID runId,String paymentId,String correlationId,UUID recommendationId,
        RoutingAgentRun.Status status,String configuredModel,String promptVersion,Instant createdAt,Instant deadlineAt,
        Instant finishedAt,String failureCode,AgentReport report) {
    @Override public String toString(){return "AgentRunView[runId="+runId+",status="+status+"]";}
    static AgentRunView of(RoutingAgentRun r){return new AgentRunView(r.id,r.paymentId,r.correlationId,r.recommendationId,r.status,
        r.configuredModel,r.promptVersion,r.createdAt,r.deadlineAt,r.finishedAt,r.failureCode,r.report);}
}
