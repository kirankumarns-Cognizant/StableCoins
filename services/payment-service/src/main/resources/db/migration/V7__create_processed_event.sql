-- V7: Idempotent consumer deduplication table for payment-service (ADR-004)
-- Prefixed "payment_" to avoid collision with compliance-quote-service (DEC-02 shared schema).
-- Before processing any inbound event, consumer inserts (event_id, consumer_name).
-- Duplicate delivery is detected by the unique constraint — DataIntegrityViolationException → skip.
CREATE TABLE payment_processed_event (
    event_id      VARCHAR(36)  NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_name)
);
