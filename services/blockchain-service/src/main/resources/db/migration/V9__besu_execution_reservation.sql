-- Blockchain-owned execution-time evidence; owner claim is checked read-only.
-- Atomic with HELD -> PINNED. No nonce, signed payload, legacy USDC row or journal.
CREATE TABLE besu_execution_reservation (
 execution_id UUID PRIMARY KEY REFERENCES besu_liquidity_commitment(execution_id),
 document JSONB NOT NULL CHECK(jsonb_typeof(document)='object'),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER besu_execution_reservation_no_mutation BEFORE UPDATE OR DELETE ON besu_execution_reservation
 FOR EACH ROW EXECUTE FUNCTION besu_observation_immutable();