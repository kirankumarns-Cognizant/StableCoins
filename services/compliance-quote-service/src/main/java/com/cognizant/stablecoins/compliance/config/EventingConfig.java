package com.cognizant.stablecoins.compliance.config;

import com.cognizant.stablecoins.compliance.event.publisher.AwsEventBridgePublisher;
import com.cognizant.stablecoins.compliance.event.publisher.DomainEventPublisher;
import com.cognizant.stablecoins.compliance.event.publisher.LocalSqsEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.util.Map;

/**
 * Wires EventBridge / SQS clients and selects the correct DomainEventPublisher.
 */
@Configuration
@EnableScheduling
public class EventingConfig {

    @Value("${connections.aws.endpoint:}")
    private String localstackUrl;

    @Value("${connections.aws.account-id:000000000000}")
    private String localstackAccountId;

    @Value("${connections.aws.region:us-west-2}")
    private String region;

    @Value("${connections.aws.local-access-key:test}")
    private String accessKey;

    @Value("${connections.aws.local-secret-key:test}")
    private String secretKey;

    @Value("${app.eventbridge.bus-name:stablecoin-payment-bus}")
    private String eventBusName;

    @Value("${app.eventbridge.source:stablecoins.compliance-quote-service}")
    private String eventSource;

    @Value("${app.sqs.compliance-completed-queue-name}")
    private String complianceCompletedQueue;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqsClient sqsClient() {
        var builder = SqsClient.builder()
                .region(Region.of(region));
        if (!localstackUrl.isBlank()) {
            builder.endpointOverride(URI.create(localstackUrl));
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventBridgeClient eventBridgeClient() {
        var builder = EventBridgeClient.builder()
                .region(Region.of(region));
        if (!localstackUrl.isBlank()) {
            builder.endpointOverride(URI.create(localstackUrl));
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }

    @Bean
    public DomainEventPublisher domainEventPublisher(SqsClient sqsClient,
                                                      EventBridgeClient eventBridgeClient,
                                                      ObjectMapper objectMapper) {
        if (!localstackUrl.isBlank()) {
            String base = localstackUrl + "/" + localstackAccountId + "/";
            Map<String, String> queueMap = Map.of(
                    "ComplianceCompleted", base + complianceCompletedQueue
            );
            return new LocalSqsEventPublisher(sqsClient, objectMapper, queueMap);
        }
        return new AwsEventBridgePublisher(eventBridgeClient, objectMapper, eventBusName, eventSource);
    }
}
