-- V3: Immutable double-entry lines. Rows must not be modified once journal is POSTED.
CREATE TABLE ledger_entry (
    entry_id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_id         UUID           NOT NULL REFERENCES ledger_journal(journal_id),
    ledger_account_id  UUID           NOT NULL REFERENCES ledger_account(ledger_account_id),
    payment_id         VARCHAR(30)    NOT NULL,
    asset              VARCHAR(10)    NOT NULL CHECK (asset IN ('USD','USDC','ETH')),
    debit              NUMERIC(18,6)  NOT NULL DEFAULT 0 CHECK (debit >= 0),
    credit             NUMERIC(18,6)  NOT NULL DEFAULT 0 CHECK (credit >= 0),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    reversal_of        UUID           REFERENCES ledger_entry(entry_id)
);
CREATE INDEX idx_ledger_entry_journal ON ledger_entry(journal_id);
CREATE INDEX idx_ledger_entry_payment ON ledger_entry(payment_id);
