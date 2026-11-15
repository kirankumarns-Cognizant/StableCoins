-- V2: Transactional outbox table for compliance-quote-service (ADR-004)
-- Prefixed "compliance_" to avoid collision with payment-service (DEC-02 shared schema).
CREATE TABLE compliance_outbox_event (
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

CREATE INDEX idx_compliance_outbox_status ON compliance_outbox_event(status, created_at);
