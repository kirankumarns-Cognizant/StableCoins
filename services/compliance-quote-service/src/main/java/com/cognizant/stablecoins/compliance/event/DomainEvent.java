package com.cognizant.stablecoins.compliance.event;

import java.time.OffsetDateTime;

/**
 * Typed event envelope (AGENTS.md §13).
 *
 * @param <T> payload type
 */
public class DomainEvent<T> {

    private String eventId;
    private String eventType;
    private String eventVersion;
    private String paymentId;
    private String correlationId;
    private OffsetDateTime timestamp;
    private T payload;

    public DomainEvent() {}

    public DomainEvent(String eventId, String eventType, String eventVersion,
                       String paymentId, String correlationId, T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.paymentId = paymentId;
        this.correlationId = correlationId;
        this.timestamp = OffsetDateTime.now();
        this.payload = payload;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventVersion() { return eventVersion; }
    public void setEventVersion(String eventVersion) { this.eventVersion = eventVersion; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }
}
