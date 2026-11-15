package com.cognizant.stablecoins.payment.agent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.regions.Region;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static com.cognizant.stablecoins.payment.routing.SimulatedRoutingScenarios.Scenario.*;
import static org.assertj.core.api.Assertions.*;
/** Explicit opt-in: real provider, real Secrets Manager, labeled fixture signals; no financial command. */
@EnabledIfEnvironmentVariable(named="RUN_REAL_MODEL_TESTS",matches="true")
class RoutingAgentLiveTest {
    @Test void actualModelUsesReadToolsAndReturnsGroundedRecommendation(){
        var p=provider();p.setModel(required("AI_MODEL"));p.setProvider(AgentProviderProperties.Provider.valueOf(System.getenv().getOrDefault("AI_PROVIDER","OPENAI")));
        p.setAuthMode(AgentProviderProperties.AuthMode.valueOf(System.getenv().getOrDefault("AI_AUTH_MODE","SECRET_BEARER")));
        p.setRegion(System.getenv().getOrDefault("AI_REGION",System.getenv().getOrDefault("AWS_REGION","us-east-1")));
        p.setApiKeySecretName(p.getAuthMode()==AgentProviderProperties.AuthMode.SECRET_BEARER?required("AI_API_KEY_SECRET_NAME"):"");
        p.setBaseUrl(System.getenv().getOrDefault("AI_BASE_URL","https://api.openai.com/v1"));
        var settings=settings();settings.setDeadlineMs(90000);settings.setInputUsdPerMillion(new BigDecimal(required("ROUTING_AGENT_INPUT_USD_PER_MILLION")));
        settings.setOutputUsdPerMillion(new BigDecimal(required("ROUTING_AGENT_OUTPUT_USD_PER_MILLION")));
        settings.setMaximumInferenceCostUsd(new BigDecimal(required("ROUTING_AGENT_MAXIMUM_INFERENCE_COST_USD")));
        var material=material(NORMAL,1);String region=System.getenv().getOrDefault("AWS_REGION","us-east-1");
        var keys=new SecretsManagerAgentKeyProvider(()->SecretsManagerClient.builder().region(Region.of(region)).overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(5)).retryPolicy(r->r.numRetries(0))).build(),p);
        var agent=new RoutingAgent(new OpenAiResponsesModel(p,settings,p.getAuthMode()==AgentProviderProperties.AuthMode.AWS_SIGV4?
            new BedrockSigV4Authorizer(p,software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create()):
            (endpoint,body)->Map.of("Authorization","Bearer "+keys.load()),JSON),settings,p,(payment,id,actor,latest)->material,ASSESSOR,JSON);
        var report=agent.run("PAY-AGENT",SEED,ACTOR,"Recommend the lowest-cost eligible test-USDC settlement and simulated INR payout for the confirmed intent. Inspect all seven signal types and refresh before finalizing.");
        assertThat(report.answer().selectedCandidateId()).isEqualTo("SIMULATED-A");assertThat(report.tools()).isNotEmpty();assertThat(report.inputTokens()).isPositive();
        // Deliberately no raw requests/responses, prompts or private reasoning in console or artifacts.
    }
    private String required(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("Missing required live-test configuration: "+key);return value;}
}
