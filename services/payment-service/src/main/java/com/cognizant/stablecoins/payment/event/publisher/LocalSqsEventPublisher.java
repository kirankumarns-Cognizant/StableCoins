package com.cognizant.stablecoins.payment.event.publisher;

import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

/**
 * Publishes domain events directly to SQS (local dev / LocalStack path).
 * Skips EventBridge routing — the OutboxDispatcher sends directly to the target queue.
 * Queue names are looked up by event type.
 */
public class LocalSqsEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalSqsEventPublisher.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> eventTypeToQueueUrl;
    private Map<String, java.util.List<String>> fanoutRoutes=Map.of();
    public LocalSqsEventPublisher withFanoutRoutes(Map<String,java.util.List<String>> routes){this.fanoutRoutes=Map.copyOf(routes);return this;}

    public LocalSqsEventPublisher(SqsClient sqsClient, ObjectMapper objectMapper,
                                   Map<String, String> eventTypeToQueueUrl) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.eventTypeToQueueUrl = eventTypeToQueueUrl;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        java.util.List<String> urls=fanoutRoutes.get(event.getEventType());
        String queueUrl = eventTypeToQueueUrl.get(event.getEventType());
        if(urls==null && queueUrl!=null)urls=java.util.List.of(queueUrl);
        if (urls == null || urls.isEmpty()) {
            log.warn("service=payment-service event=no_queue_mapping eventType={} eventId={} — skipping publish",
                    event.getEventType(), event.getEventId());
            throw new IllegalStateException("No configured queue for event type " + event.getEventType());
        }
        try {
            String body = objectMapper.writeValueAsString(event);
            for(String destination:urls) sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(destination)
                    .messageBody(body)
                    .build());
            log.info("service=payment-service event=sqs_published eventId={} eventType={} paymentId={} correlationId={} queue={}",
                    event.getEventId(), event.getEventType(), event.getPaymentId(),
                    event.getCorrelationId(), queueUrl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event " + event.getEventId() + " to SQS", e);
        }
    }
}
