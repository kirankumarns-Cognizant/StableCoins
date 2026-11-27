-- Read-only private evidence requests. Never an executable recommendation or reservation.
CREATE TABLE payment_settlement_preparation (
 request_id UUID PRIMARY KEY,
 instruction_id UUID NOT NULL,
 instruction_phase VARCHAR(12) NOT NULL DEFAULT 'INSTRUCTION' CHECK(instruction_phase='INSTRUCTION'),
 payment_id VARCHAR(50) NOT NULL REFERENCES payment(payment_id),
 correlation_id VARCHAR(60) NOT NULL,
 actor_id VARCHAR(255) NOT NULL,
 document JSONB NOT NULL CHECK(jsonb_typeof(document)='object'),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(instruction_id,instruction_phase,payment_id,correlation_id)
 REFERENCES payment_settlement_binding(binding_id,phase,payment_id,correlation_id)
);
CREATE INDEX settlement_preparation_payment ON payment_settlement_preparation(payment_id,created_at);
CREATE TRIGGER settlement_preparation_no_mutation BEFORE UPDATE OR DELETE ON payment_settlement_preparation
 FOR EACH ROW EXECUTE FUNCTION payment_settlement_binding_immutable();
