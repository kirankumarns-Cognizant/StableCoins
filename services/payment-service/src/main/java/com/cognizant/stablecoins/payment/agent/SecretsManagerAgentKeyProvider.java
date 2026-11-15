package com.cognizant.stablecoins.payment.agent;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import java.util.function.Supplier;
public class SecretsManagerAgentKeyProvider implements AgentKeyProvider {
    private final Supplier<SecretsManagerClient> clients;private final AgentProviderProperties properties;
    public SecretsManagerAgentKeyProvider(Supplier<SecretsManagerClient> clients,AgentProviderProperties properties){this.clients=clients;this.properties=properties;}
    public String load(){
        properties.validate();
        try(var client=clients.get()){
            String key=client.getSecretValue(GetSecretValueRequest.builder().secretId(properties.getApiKeySecretName()).build()).secretString();
            if(key==null||key.isBlank()||key.length()>8192||!key.matches("[A-Za-z0-9._~+/:=-]+"))throw new AgentFailure("AGENT_SECRET_UNAVAILABLE");
            return key;
        }catch(Exception e){throw new AgentFailure("AGENT_SECRET_UNAVAILABLE");}
    }
}
