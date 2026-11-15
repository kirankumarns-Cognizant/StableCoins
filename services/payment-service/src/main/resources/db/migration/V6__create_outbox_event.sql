-- V6: Transactional outbox table for payment-service (ADR-004)
-- Prefixed "payment_" to avoid collision with compliance-quote-service (DEC-02 shared schema).
-- Events written here in the same DB transaction as PAYMENT row changes.
-- OutboxDispatcher reads PENDING rows and publishes to EventBridge / SQS.
CREATE TABLE payment_outbox_event (
    event_id       VARCHAR(36)  PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    event_version  VARCHAR(10)  NOT NULL DEFAULT '1',
    aggregate_id   VARCHAR(50)  NOT NULL,
    correlation_id VARCHAR(60)  NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_payment_outbox_status ON payment_outbox_event(status, created_at);
