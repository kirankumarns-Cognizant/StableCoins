-- V3: Idempotent consumer deduplication table for compliance-quote-service (ADR-004)
-- Prefixed "compliance_" to avoid collision with payment-service (DEC-02 shared schema).
-- Before processing any inbound event, consumer inserts (event_id, consumer_name).
-- Duplicate delivery is detected by the unique constraint — DataIntegrityViolationException → skip.
CREATE TABLE compliance_processed_event (
    event_id      VARCHAR(36)  NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_name)
);
