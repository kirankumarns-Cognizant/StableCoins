package com.cognizant.stablecoins.payment.event.publisher;

import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

/**
 * Publishes domain events to AWS EventBridge (production path).
 * EventBridge routes events to SQS queues via rules defined in CDK.
 *
 * <p>Bus name and source are injected from application properties:
 * app.eventbridge.bus-name and app.eventbridge.source
 */
public class AwsEventBridgePublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AwsEventBridgePublisher.class);

    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    private final String eventBusName;
    private final String eventSource;

    public AwsEventBridgePublisher(EventBridgeClient eventBridgeClient,
                                    ObjectMapper objectMapper,
                                    String eventBusName,
                                    String eventSource) {
        this.eventBridgeClient = eventBridgeClient;
        this.objectMapper = objectMapper;
        this.eventBusName = eventBusName;
        this.eventSource = eventSource;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        try {
            String detail = objectMapper.writeValueAsString(event);
            PutEventsRequest request = PutEventsRequest.builder()
                    .entries(PutEventsRequestEntry.builder()
                            .eventBusName(eventBusName)
                            .source(eventSource)
                            .detailType(event.getEventType())
                            .detail(detail)
                            .build())
                    .build();
            var response = eventBridgeClient.putEvents(request);
            if (response.failedEntryCount() > 0) {
                log.error("service=payment-service event=eventbridge_put_failed eventType={} paymentId={} failedCount={}",
                        event.getEventType(), event.getPaymentId(), response.failedEntryCount());
                throw new RuntimeException("EventBridge PutEvents failed for event " + event.getEventId());
            }
            log.info("service=payment-service event=eventbridge_published eventId={} eventType={} paymentId={} correlationId={}",
                    event.getEventId(), event.getEventType(), event.getPaymentId(), event.getCorrelationId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event " + event.getEventId(), e);
        }
    }
}
