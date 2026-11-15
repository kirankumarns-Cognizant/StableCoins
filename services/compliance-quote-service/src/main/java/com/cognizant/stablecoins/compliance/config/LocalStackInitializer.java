package com.cognizant.stablecoins.compliance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException;

/**
 * Creates SQS queues in LocalStack on startup (local profile only).
 */
@Component
@Profile("local")
public class LocalStackInitializer {

    private static final Logger log = LoggerFactory.getLogger(LocalStackInitializer.class);

    private final SqsClient sqsClient;

    @Value("${connections.aws.endpoint:}")
    private String localstackUrl;

    public LocalStackInitializer(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    @Value("${app.sqs.payment-created-queue-name}")
    private String paymentCreatedQueue;

    @Value("${app.sqs.compliance-completed-queue-name}")
    private String complianceCompletedQueue;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (localstackUrl.isBlank()) {
            throw new IllegalStateException("Local profile requires LOCALSTACK_URL; refusing AWS queue creation");
        }
        createQueue(paymentCreatedQueue);
        createQueue(complianceCompletedQueue);
        log.info("service=compliance-quote-service event=localstack_queues_ready localstackUrl={}", localstackUrl);
    }

    private void createQueue(String name) {
        try {
            sqsClient.createQueue(CreateQueueRequest.builder().queueName(name).build());
            log.info("service=compliance-quote-service event=sqs_queue_created name={}", name);
        } catch (QueueNameExistsException e) {
            log.debug("service=compliance-quote-service event=sqs_queue_exists name={}", name);
        } catch (Exception e) {
            log.warn("service=compliance-quote-service event=sqs_queue_create_failed name={} error={}", name, e.getMessage());
        }
    }
}
