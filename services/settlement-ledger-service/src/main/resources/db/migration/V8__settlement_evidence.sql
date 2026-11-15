ALTER TABLE settlement ADD COLUMN correlation_id VARCHAR(60),ADD COLUMN transaction_hash VARCHAR(66),ADD COLUMN fee_journal_id UUID REFERENCES ledger_journal(journal_id);
CREATE UNIQUE INDEX settlement_payment_type_unique ON settlement(payment_id,settlement_type);
