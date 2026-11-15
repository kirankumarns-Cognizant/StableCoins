-- Optional versioned intent and server policy snapshot, owned by payment-service.
-- Old rows and old API requests retain their existing fiat destination semantics.
ALTER TABLE payment ADD COLUMN intent_snapshot JSONB;
ALTER TABLE payment ALTER COLUMN destination_currency DROP NOT NULL;
ALTER TABLE payment ADD CONSTRAINT ck_payment_destination_currency
    CHECK (destination_currency IS NOT NULL OR intent_snapshot IS NOT NULL);
