-- V4: Settlement state tracking
CREATE TABLE settlement (
    settlement_id    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id       VARCHAR(30)    NOT NULL,
    journal_id       UUID           NOT NULL REFERENCES ledger_journal(journal_id),
    settlement_type  VARCHAR(20)    NOT NULL CHECK (settlement_type IN ('BLOCKCHAIN','VISA')),
    asset            VARCHAR(10)    NOT NULL CHECK (asset IN ('USD','USDC','ETH')),
    amount           NUMERIC(18,6)  NOT NULL CHECK (amount > 0),
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','POSTED','COMPLETED','FAILED','REVERSED')),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    settled_at       TIMESTAMPTZ
);
CREATE INDEX idx_settlement_payment ON settlement(payment_id);
