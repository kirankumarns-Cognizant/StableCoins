CREATE TABLE blockchain_outbox_event (
    event_id         UUID PRIMARY KEY,
    payment_id       UUID NOT NULL,
    correlation_id   UUID,
    event_type       VARCHAR(100) NOT NULL,
    event_version    VARCHAR(10) NOT NULL DEFAULT '1.0',
    payload          TEXT NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count      INT NOT NULL DEFAULT 0,
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blockchain_outbox_status ON blockchain_outbox_event(status, created_at);
