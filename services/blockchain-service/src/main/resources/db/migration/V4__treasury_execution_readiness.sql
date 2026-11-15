-- Preserve payment-service opaque identifiers; existing UUID values remain their string form.
ALTER TABLE blockchain_transaction ALTER COLUMN payment_id TYPE VARCHAR(64) USING payment_id::text;
ALTER TABLE blockchain_transaction ALTER COLUMN correlation_id TYPE VARCHAR(64) USING correlation_id::text;
ALTER TABLE blockchain_outbox_event ALTER COLUMN payment_id TYPE VARCHAR(64) USING payment_id::text;
ALTER TABLE blockchain_outbox_event ALTER COLUMN correlation_id TYPE VARCHAR(64) USING correlation_id::text;
ALTER TABLE blockchain_wallet DROP CONSTRAINT blockchain_wallet_wallet_address_key;
UPDATE blockchain_wallet SET wallet_address=lower(wallet_address);
UPDATE blockchain_transaction SET from_address=lower(from_address);
ALTER TABLE blockchain_wallet ADD CONSTRAINT uq_wallet_address_network UNIQUE(wallet_address,network);
-- Stop migration for duplicate payment legs across networks; operator investigation is required.
ALTER TABLE blockchain_transaction ADD CONSTRAINT uq_blockchain_payment_operation UNIQUE(payment_id);
ALTER TABLE blockchain_transaction ADD COLUMN chain_id BIGINT;
ALTER TABLE blockchain_transaction ADD COLUMN gas_limit NUMERIC(36,0);
ALTER TABLE blockchain_transaction ADD COLUMN gas_price_wei NUMERIC(36,0);
ALTER TABLE blockchain_transaction ADD COLUMN gas_reserve_eth NUMERIC(36,18);
ALTER TABLE blockchain_transaction ADD COLUMN evidence_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY_UNKNOWN';
ALTER TABLE blockchain_transaction ADD COLUMN reservation_status VARCHAR(20) NOT NULL DEFAULT 'LEGACY_REVIEW';
ALTER TABLE blockchain_transaction ADD COLUMN reservation_expires_at TIMESTAMPTZ;
-- Legacy operations are deliberately not backfilled with invented balance or gas evidence.
ALTER TABLE blockchain_transaction ADD CONSTRAINT chk_reservation_status CHECK
 (reservation_status IN ('HELD','PINNED','RELEASED','EXPIRED','SETTLED','LEGACY_REVIEW'));
ALTER TABLE blockchain_transaction ADD CONSTRAINT chk_reserved_gas CHECK (gas_reserve_eth IS NULL OR gas_reserve_eth>0);
CREATE INDEX idx_blockchain_reservation_wallet ON blockchain_transaction(from_address,network,reservation_status);
CREATE TABLE blockchain_execution_audit (
 audit_id UUID PRIMARY KEY, transaction_id UUID NOT NULL REFERENCES blockchain_transaction(transaction_id),
 payment_id VARCHAR(64) NOT NULL, correlation_id VARCHAR(64) NOT NULL,
 status VARCHAR(30) NOT NULL, reservation_status VARCHAR(20) NOT NULL,
 reason VARCHAR(100) NOT NULL, timestamp TIMESTAMPTZ NOT NULL
);

ALTER TABLE blockchain_transaction ADD CONSTRAINT fk_blockchain_owned_wallet
 FOREIGN KEY(from_address,network) REFERENCES blockchain_wallet(wallet_address,network);
ALTER TABLE blockchain_transaction ADD CONSTRAINT chk_active_execution_metadata CHECK (
 reservation_status NOT IN ('HELD','PINNED') OR
 (chain_id IS NOT NULL AND correlation_id IS NOT NULL AND gas_limit IS NOT NULL AND gas_limit>0 AND gas_price_wei IS NOT NULL AND gas_price_wei>0
  AND gas_reserve_eth IS NOT NULL AND gas_reserve_eth>0 AND reservation_expires_at IS NOT NULL AND evidence_mode IN ('MOCK','LIVE'))
);
ALTER TABLE blockchain_transaction ADD CONSTRAINT chk_pinned_submission CHECK (
 reservation_status<>'PINNED' OR (tx_hash IS NOT NULL AND nonce IS NOT NULL AND nonce>=0
  AND status IN ('SUBMITTING','SUBMITTED','SUBMISSION_UNKNOWN'))
);
