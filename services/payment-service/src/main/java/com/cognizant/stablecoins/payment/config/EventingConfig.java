package com.cognizant.stablecoins.payment.config;

import com.cognizant.stablecoins.payment.event.publisher.AwsEventBridgePublisher;
import com.cognizant.stablecoins.payment.event.publisher.DomainEventPublisher;
import com.cognizant.stablecoins.payment.event.publisher.LocalSqsEventPublisher;
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
 *
 * <p>When LOCALSTACK_URL is set (local dev): uses LocalSqsEventPublisher and
 * configures all SDK clients with endpoint override.
 * <p>When LOCALSTACK_URL is blank (AWS): uses AwsEventBridgePublisher with
 * default SDK endpoint resolution.
 */
@Configuration
@EnableScheduling
public class EventingConfig {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.core.env.Environment environment;

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

    @Value("${app.eventbridge.source:stablecoins.payment-service}")
    private String eventSource;

    @Value("${app.sqs.payment-created-queue-name}")
    private String paymentCreatedQueue;

    @Value("${app.sqs.compliance-completed-queue-name}")
    private String complianceCompletedQueue;

    @Value("${app.sqs.routing-decisions-queue-name:stablecoin-routing-decisions-queue}")
    private String routingDecisionsQueue;

    @Value("${app.sqs.ledger-funding-queue-name}")
    private String ledgerFundingQueue;

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
            // Local: publish directly to SQS queues via LocalStack
            String base = localstackUrl + "/" + localstackAccountId + "/";
            Map<String, String> queueMap = new java.util.HashMap<>();
            queueMap.put("PaymentCreated", base + paymentCreatedQueue);
            queueMap.put("StablecoinFundingCompleted", base + ledgerFundingQueue);
            if (environment.getProperty("app.besu.funding-events.enabled", Boolean.class, false)) {
                String queue = environment.getRequiredProperty("app.sqs.besu-ledger-funding-queue-name");
                if (queue.isBlank() || queue.equals(ledgerFundingQueue))
                    throw new IllegalStateException("Dedicated Besu funding queue required");
                queueMap.put("BesuFundingAuthorized", base + queue);
            }
            queueMap.put("ComplianceCompleted", base + complianceCompletedQueue);
            for (String type : com.cognizant.stablecoins.payment.approval.RoutingDecisionEvents.TYPES)
                queueMap.put(type, base + routingDecisionsQueue);
            for(String type:java.util.List.of("SettlementCompleted","ReconciliationCompleted"))queueMap.put(type,base+routingDecisionsQueue);
            // Audit notifications only. Private continuation polls committed owner evidence, not legacy commands.
            if(environment.getProperty("app.besu.closure.enabled",Boolean.class,false))
                for(String type:java.util.List.of("BesuBlockchainTransactionSubmitted","BesuBlockchainTransactionConfirmed",
                        "BesuSettlementCompleted","BesuReconciliationRequested","BesuReconciliationCompleted"))
                    queueMap.put(type,base+routingDecisionsQueue);
            if(environment.getProperty("app.besu.recovery.enabled",Boolean.class,false))
                for(String type:java.util.List.of("BesuPaymentFailed","BesuCompensationAuthorized"))
                    queueMap.put(type,base+routingDecisionsQueue);
            var publisher=new LocalSqsEventPublisher(sqsClient, objectMapper, queueMap);
            if(environment.getProperty("app.flow.enabled",Boolean.class,false)) publisher.withFanoutRoutes(Map.of(
                "SettlementObservationRequested",java.util.List.of(base+environment.getRequiredProperty("app.sqs.flow-treasury-queue-name")),
                "RoutingEvidenceRequested",java.util.List.of(base+environment.getRequiredProperty("app.sqs.flow-quote-queue-name"),base+environment.getRequiredProperty("app.sqs.flow-treasury-queue-name")),
                "StablecoinExecutionRequested",java.util.List.of(base+environment.getRequiredProperty("app.sqs.flow-validation-queue-name"),base+environment.getRequiredProperty("app.sqs.flow-reservation-queue-name"),base+environment.getRequiredProperty("app.sqs.visa-payout-queue-name")),
                "BlockchainSubmissionRequested",java.util.List.of(base+environment.getRequiredProperty("app.sqs.flow-submission-queue-name")),
                "SettlementRequested",java.util.List.of(base+environment.getRequiredProperty("app.sqs.settlement-requested-queue-name")),
                "VisaPayoutInitiated",java.util.List.of(base+environment.getRequiredProperty("app.sqs.visa-payout-queue-name"),base+environment.getRequiredProperty("app.sqs.ledger-payout-queue-name")),
                "VisaPayoutCompleted",java.util.List.of(base+environment.getRequiredProperty("app.sqs.ledger-payout-queue-name")),
                "ReconciliationRequested",java.util.List.of(base+environment.getRequiredProperty("app.sqs.reconciliation-requested-queue-name"))));
            return publisher;
        }
        // AWS: publish via EventBridge → SQS routing rules (CDK)
        var publisher = new AwsEventBridgePublisher(eventBridgeClient, objectMapper, eventBusName, eventSource);
        return event -> {
            if ("BesuFundingAuthorized".equals(event.getEventType()) &&
                    !environment.getProperty("app.besu.funding-events.enabled", Boolean.class, false))
                throw new IllegalStateException("Besu funding transport disabled");
            publisher.publish(event);
        };
    }
}
