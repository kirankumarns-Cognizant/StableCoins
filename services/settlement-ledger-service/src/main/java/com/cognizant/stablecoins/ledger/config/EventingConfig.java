package com.cognizant.stablecoins.ledger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Wires SQS client for consuming BlockchainTransactionConfirmed events.
 * Consumer-only service — no EventBridgeClient needed.
 */
@Configuration
public class EventingConfig {

    @Value("${connections.aws.endpoint:}")
    private String localstackUrl;

    @Value("${connections.aws.region:us-west-2}")
    private String region;

    @Value("${connections.aws.local-access-key:test}")
    private String accessKey;

    @Value("${connections.aws.local-secret-key:test}")
    private String secretKey;

    @Bean
    @ConditionalOnMissingBean
    public SqsClient sqsClient() {
        var builder = SqsClient.builder()
                .region(Region.of(region));
        if (localstackUrl != null && !localstackUrl.isBlank()) {
            builder.endpointOverride(URI.create(localstackUrl));
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }
}
