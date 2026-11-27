-- Immutable private quote drafts only. Not a routing recommendation, approval or execution claim.
-- V13 approved-review foreign keys and all legacy public payment records remain unchanged.
ALTER TABLE payment_settlement_preparation ADD CONSTRAINT settlement_preparation_scope
    UNIQUE(request_id,instruction_id,payment_id,correlation_id);

CREATE TABLE payment_settlement_quote (
 review_id UUID PRIMARY KEY,
 quote_id UUID NOT NULL UNIQUE,
 request_id UUID NOT NULL,
 instruction_id UUID NOT NULL,
 payment_id VARCHAR(50) NOT NULL REFERENCES payment(payment_id),
 correlation_id VARCHAR(60) NOT NULL,
 actor_id VARCHAR(255) NOT NULL,
 document JSONB NOT NULL CHECK(jsonb_typeof(document)='object'),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(request_id,instruction_id,payment_id,correlation_id)
 REFERENCES payment_settlement_preparation(request_id,instruction_id,payment_id,correlation_id)
);
CREATE INDEX settlement_quote_payment ON payment_settlement_quote(payment_id,created_at);
CREATE TRIGGER settlement_quote_no_mutation BEFORE UPDATE OR DELETE ON payment_settlement_quote
 FOR EACH ROW EXECUTE FUNCTION payment_settlement_binding_immutable();
