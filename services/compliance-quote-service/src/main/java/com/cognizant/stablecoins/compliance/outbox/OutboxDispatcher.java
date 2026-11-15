package com.cognizant.stablecoins.compliance.outbox;

import com.cognizant.stablecoins.compliance.event.DomainEvent;
import com.cognizant.stablecoins.compliance.event.publisher.DomainEventPublisher;
import com.cognizant.stablecoins.compliance.model.OutboxEvent;
import com.cognizant.stablecoins.compliance.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls the COMPLIANCE_OUTBOX_EVENT table for PENDING records and publishes them.
 * Runs every 5 seconds.
 *
 * <p>Row locking: findPendingForUpdate() uses PESSIMISTIC_WRITE + SKIP LOCKED so that
 * concurrent instances claim disjoint rows and avoid duplicate publish.
 *
 * <p>Retry policy: on failure, retry_count is incremented. After 5 failures the record
 * is moved to FAILED status and excluded from future polls.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxDispatcher(OutboxEventRepository outboxEventRepository,
                             DomainEventPublisher domainEventPublisher,
                             ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void dispatch() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingForUpdate();
        for (OutboxEvent record : pending) {
            publishOne(record);
        }
    }

    @Transactional
    public void publishOne(OutboxEvent record) {
        try {
            DomainEvent<?> event = objectMapper.readValue(record.getPayload(),
                    new TypeReference<DomainEvent<Object>>() {});
            domainEventPublisher.publish(event);
            record.setStatus("PUBLISHED");
            record.setPublishedAt(OffsetDateTime.now());
            outboxEventRepository.save(record);
            log.info("service=compliance-quote-service event=outbox_published eventId={} eventType={} paymentId={}",
                    record.getEventId(), record.getEventType(), record.getAggregateId());
        } catch (Exception e) {
            int retries = record.getRetryCount() + 1;
            record.setRetryCount(retries);
            String errorMsg = e.getMessage();
            record.setLastError(errorMsg != null && errorMsg.length() > 500
                    ? errorMsg.substring(0, 500) : errorMsg);
            if (retries >= MAX_RETRIES) {
                record.setStatus("FAILED");
                log.error("service=compliance-quote-service event=outbox_publish_failed_terminal eventId={} eventType={} retries={} error={}",
                        record.getEventId(), record.getEventType(), retries, e.getMessage());
            } else {
                log.warn("service=compliance-quote-service event=outbox_publish_failed_retryable eventId={} eventType={} retries={} error={}",
                        record.getEventId(), record.getEventType(), retries, e.getMessage());
            }
            outboxEventRepository.save(record);
        }
    }
}
