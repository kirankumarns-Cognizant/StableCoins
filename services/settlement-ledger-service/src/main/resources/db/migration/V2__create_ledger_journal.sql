-- V2: Double-entry journal header table
-- UNIQUE(payment_id, journal_type) enforces idempotent posting.
CREATE TABLE ledger_journal (
    journal_id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id               VARCHAR(30)   NOT NULL,
    correlation_id           VARCHAR(60)   NOT NULL,
    journal_type             VARCHAR(50)   NOT NULL,
    reference                VARCHAR(100),
    description              VARCHAR(255),
    status                   VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING','POSTED','REVERSED')),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    posted_at                TIMESTAMPTZ,
    reversed_by_journal_id   UUID          REFERENCES ledger_journal(journal_id),
    CONSTRAINT uq_journal_payment_type UNIQUE (payment_id, journal_type)
);
CREATE INDEX idx_ledger_journal_payment ON ledger_journal(payment_id);
