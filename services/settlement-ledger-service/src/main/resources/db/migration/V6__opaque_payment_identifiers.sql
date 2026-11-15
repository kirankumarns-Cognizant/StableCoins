ALTER TABLE ledger_journal ALTER COLUMN payment_id TYPE VARCHAR(64);
ALTER TABLE ledger_journal ALTER COLUMN correlation_id TYPE VARCHAR(64);
ALTER TABLE ledger_entry ALTER COLUMN payment_id TYPE VARCHAR(64);
ALTER TABLE settlement ALTER COLUMN payment_id TYPE VARCHAR(64);
