package com.cognizant.stablecoins.compliance.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Idempotent consumer deduplication record for compliance-quote-service (ADR-004).
 */
@Entity
@Table(name = "compliance_processed_event")
@IdClass(ProcessedEventId.class)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Id
    @Column(name = "consumer_name", length = 100)
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    @PrePersist
    void prePersist() {
        if (processedAt == null) processedAt = OffsetDateTime.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getConsumerName() { return consumerName; }
    public void setConsumerName(String consumerName) { this.consumerName = consumerName; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
}
