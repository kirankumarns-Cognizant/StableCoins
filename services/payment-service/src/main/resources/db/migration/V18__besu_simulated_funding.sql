-- Payment-owned acknowledgement, separate from legacy USDC payment_execution.
ALTER TABLE payment_settlement_execution_claim ADD CONSTRAINT besu_funding_claim_scope
 UNIQUE(execution_id,payment_id,correlation_id,customer_id);
CREATE TABLE payment_besu_funding (
 execution_id UUID PRIMARY KEY,
 payment_id VARCHAR(50) NOT NULL UNIQUE,
 correlation_id VARCHAR(60) NOT NULL,
 customer_id UUID NOT NULL,
 account_id UUID NOT NULL REFERENCES account(account_id),
 source_usd NUMERIC(18,2) NOT NULL CHECK(source_usd>0),
 balance_before NUMERIC(18,2) NOT NULL,
 balance_after NUMERIC(18,2) NOT NULL CHECK(balance_after>=0),
 actor_id VARCHAR(255) NOT NULL,
 event_id VARCHAR(36) NOT NULL UNIQUE REFERENCES payment_outbox_event(event_id),
 funded_at TIMESTAMPTZ NOT NULL,
 funding_reference VARCHAR(64) NOT NULL UNIQUE,
 execution_document JSONB NOT NULL CHECK(jsonb_typeof(execution_document)='object'),
 execution_fingerprint VARCHAR(64) NOT NULL CHECK(execution_fingerprint ~ '^[0-9a-f]{64}$'),
 CONSTRAINT besu_funding_balanced_debit CHECK(balance_before-balance_after=source_usd),
 CHECK(funding_reference='SIMULATED-BESU-' || execution_id::text),
 FOREIGN KEY(execution_id,payment_id,correlation_id,customer_id)
 REFERENCES payment_settlement_execution_claim(execution_id,payment_id,correlation_id,customer_id)
);
CREATE TRIGGER payment_besu_funding_no_mutation BEFORE UPDATE OR DELETE ON payment_besu_funding
 FOR EACH ROW EXECUTE FUNCTION payment_settlement_binding_immutable();
