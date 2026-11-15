package com.cognizant.stablecoins.payment.agent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.*;
import java.time.Duration;
import java.net.URI;
@Configuration
public class AgentConfiguration {
    @Bean AgentKeyProvider agentKeys(AgentProviderProperties p,@Value("${connections.aws.region:us-east-1}")String region,
            @Value("${connections.aws.endpoint:}")String endpoint,@Value("${connections.aws.local-access-key:test}")String access,
            @Value("${connections.aws.local-secret-key:test}")String secret){
        return new SecretsManagerAgentKeyProvider(()->{
            var b=SecretsManagerClient.builder().region(Region.of(region)).overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(5))
                .apiCallAttemptTimeout(Duration.ofSeconds(5)).retryPolicy(r->r.numRetries(0)));
            if(!endpoint.isBlank())b.endpointOverride(URI.create(endpoint)).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret)));
            return b.build();
        },p);
    }
    @Bean(name="agentAwsCredentials",destroyMethod="close") DefaultCredentialsProvider agentAwsCredentials(){return DefaultCredentialsProvider.create();}
    @Bean AgentRequestAuthorizer agentAuthorizer(AgentProviderProperties p,AgentKeyProvider keys,
            @org.springframework.beans.factory.annotation.Qualifier("agentAwsCredentials")DefaultCredentialsProvider credentials){
        return p.getAuthMode()==AgentProviderProperties.AuthMode.AWS_SIGV4?new BedrockSigV4Authorizer(p,credentials):
            (endpoint,body)->java.util.Map.of("Authorization","Bearer "+keys.load());
    }
    @Bean AgentModel agentModel(AgentProviderProperties p,AgentSettings limits,AgentRequestAuthorizer authorizer,ObjectMapper mapper){
        if(limits.isEnabled()){p.validate();if(limits.getAllowedCustomerIds().isEmpty())throw new IllegalArgumentException("Enabled agent requires an explicit customer allowlist");}
        return new OpenAiResponsesModel(p,limits,authorizer,mapper);}
    @Bean(name="routingAgentExecutor",destroyMethod="shutdown") ThreadPoolTaskExecutor routingAgentExecutor(AgentSettings settings){
        settings.validate();var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(settings.getMaximumConcurrentRuns());
        executor.setMaxPoolSize(settings.getMaximumConcurrentRuns());executor.setQueueCapacity(0);executor.setThreadNamePrefix("routing-agent-");
        executor.setWaitForTasksToCompleteOnShutdown(false);return executor;
    }
}
