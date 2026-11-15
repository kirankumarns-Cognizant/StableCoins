package com.cognizant.stablecoins.blockchain.service;

import com.cognizant.stablecoins.blockchain.event.DomainEvent;
import com.cognizant.stablecoins.blockchain.event.publisher.DomainEventPublisher;
import com.cognizant.stablecoins.blockchain.model.BlockchainOutboxEvent;
import com.cognizant.stablecoins.blockchain.repository.BlockchainOutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the BLOCKCHAIN_OUTBOX_EVENT table for PENDING records and publishes them.
 * Runs every 5 seconds.
 *
 * <p>Row locking: findPendingForUpdate() uses PESSIMISTIC_WRITE + SKIP LOCKED so that
 * concurrent instances claim disjoint rows and avoid duplicate publish.
 *
 * <p>Retry policy: on failure, retry_count is incremented. After 5 failures the record
 * is moved to FAILED status and excluded from future polls.
 */
@Service
public class BlockchainOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(BlockchainOutboxDispatcher.class);
    private static final int MAX_RETRIES = 5;

    private final BlockchainOutboxEventRepository outboxRepository;
    private final DomainEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public BlockchainOutboxDispatcher(BlockchainOutboxEventRepository outboxRepository,
                                       DomainEventPublisher publisher,
                                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void dispatch() {
        List<BlockchainOutboxEvent> pending = outboxRepository.findPendingForUpdate();
        for (BlockchainOutboxEvent record : pending) {
            publishOne(record);
        }
    }

    @Transactional
    public void publishOne(BlockchainOutboxEvent record) {
        try {
            DomainEvent<?> event = objectMapper.readValue(
                record.getPayload(), new TypeReference<DomainEvent<Object>>() {});
            publisher.publish(event);
            record.setStatus("PUBLISHED");
            outboxRepository.save(record);
            log.info("service=blockchain-service event=outbox_published eventId={} eventType={} paymentId={}",
                record.getEventId(), record.getEventType(), record.getPaymentId());
        } catch (Exception e) {
            int retries = record.getRetryCount() + 1;
            record.setRetryCount(retries);
            String errorMsg = e.getMessage();
            record.setLastError(errorMsg != null && errorMsg.length() > 500
                ? errorMsg.substring(0, 500) : errorMsg);
            if (retries >= MAX_RETRIES) {
                record.setStatus("FAILED");
                log.error("service=blockchain-service event=outbox_max_retries_exceeded "
                    + "eventId={} eventType={} retries={} error={}",
                    record.getEventId(), record.getEventType(), retries, e.getMessage());
            } else {
                log.warn("service=blockchain-service event=outbox_publish_failed_retryable "
                    + "eventId={} eventType={} retries={} error={}",
                    record.getEventId(), record.getEventType(), retries, e.getMessage());
            }
            outboxRepository.save(record);
        }
    }
}
