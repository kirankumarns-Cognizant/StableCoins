package com.cognizant.stablecoins.payment.agent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
/** Provider boundary. Only the application creates request schemas/tools/endpoints. */
public interface AgentModel {
    record Reply(JsonNode output,long inputTokens,long outputTokens,String responseId,String model){ }
    Reply exchange(ObjectNode request,Duration remaining);
}
