-- Private owner only. Immutable receipt is the resolution of the historical PINNED hold.
-- Terminal status, receipt, capacity resolution and audit commit atomically.
ALTER TABLE besu_signed_submission ADD CONSTRAINT besu_submission_receipt_identity UNIQUE(execution_id,scope_key,tx_hash);
ALTER TABLE besu_signed_submission DROP CONSTRAINT besu_signed_submission_status_check;
ALTER TABLE besu_signed_submission ADD CHECK(status IN ('SIGNED','ATTEMPTED','SUBMITTED','UNKNOWN','CONFIRMED','REVERTED'));
ALTER TABLE besu_submission_audit DROP CONSTRAINT besu_submission_audit_status_check;
ALTER TABLE besu_submission_audit ADD CHECK(status IN ('SIGNED','ATTEMPTED','SUBMITTED','UNKNOWN','CONFIRMED','REVERTED'));
CREATE TABLE besu_final_receipt (
 execution_id UUID PRIMARY KEY,
 scope_key VARCHAR(300) NOT NULL,
 tx_hash VARCHAR(66) NOT NULL UNIQUE,
 block_hash VARCHAR(66) NOT NULL CHECK(block_hash ~ '^0x[0-9a-f]{64}$'),
 block_number BIGINT NOT NULL CHECK(block_number>0),
 success BOOLEAN NOT NULL,
 gas_used NUMERIC(78,0) NOT NULL CHECK(gas_used>0 AND gas_used<=30000000),
 gas_price NUMERIC(78,0) NOT NULL CHECK(gas_price>=0),
 total_fee_wei NUMERIC(78,0) NOT NULL CHECK(total_fee_wei=gas_used*gas_price),
 finality_policy VARCHAR(30) NOT NULL DEFAULT 'QBFT_COMMITTED' CHECK(finality_policy='QBFT_COMMITTED'),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(execution_id,scope_key,tx_hash) REFERENCES besu_signed_submission(execution_id,scope_key,tx_hash)
);
CREATE INDEX besu_receipt_scope_block ON besu_final_receipt(scope_key,block_number);
CREATE TRIGGER besu_receipt_immutable BEFORE UPDATE OR DELETE ON besu_final_receipt
 FOR EACH ROW EXECUTE FUNCTION besu_observation_immutable();
CREATE OR REPLACE FUNCTION besu_submission_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'BESU_SIGNED_SUBMISSION_IMMUTABLE'; END IF;
 IF (NEW.execution_id,NEW.scope_key,NEW.document,NEW.fingerprint,NEW.nonce,NEW.gas_limit,
     NEW.gas_price,NEW.signed_payload,NEW.tx_hash,NEW.created_at)
    IS DISTINCT FROM
    (OLD.execution_id,OLD.scope_key,OLD.document,OLD.fingerprint,OLD.nonce,OLD.gas_limit,
     OLD.gas_price,OLD.signed_payload,OLD.tx_hash,OLD.created_at)
    OR NOT ((OLD.status='SIGNED' AND NEW.status='ATTEMPTED')
         OR (OLD.status='ATTEMPTED' AND NEW.status IN ('SUBMITTED','UNKNOWN'))
         OR (OLD.status='UNKNOWN' AND NEW.status='SUBMITTED')
         OR (OLD.status IN ('ATTEMPTED','UNKNOWN','SUBMITTED') AND NEW.status IN ('CONFIRMED','REVERTED')
             AND EXISTS (SELECT 1 FROM besu_final_receipt r WHERE r.execution_id=NEW.execution_id
                AND r.success=(NEW.status='CONFIRMED') AND r.gas_used<=NEW.gas_limit AND r.gas_price=NEW.gas_price))) THEN
   RAISE EXCEPTION 'BESU_SIGNED_SUBMISSION_IMMUTABLE';
 END IF;
 RETURN NEW;
END;
$$;
-- A receipt cannot resolve capacity without the terminal submission AND matching audit.
CREATE FUNCTION besu_receipt_atomic_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE receipt_id UUID;
BEGIN
 receipt_id:=NEW.execution_id;
 IF EXISTS (SELECT 1 FROM besu_final_receipt WHERE execution_id=receipt_id)
    AND NOT EXISTS (SELECT 1 FROM besu_final_receipt r JOIN besu_signed_submission s USING(execution_id)
        JOIN besu_submission_audit a ON a.execution_id=s.execution_id AND a.status=s.status
        WHERE r.execution_id=receipt_id AND s.status=CASE WHEN r.success THEN 'CONFIRMED' ELSE 'REVERTED' END
            AND r.gas_used<=s.gas_limit AND r.gas_price=s.gas_price) THEN
   RAISE EXCEPTION 'BESU_RECEIPT_RESOLUTION_NOT_ATOMIC';
 END IF;
 RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER besu_receipt_atomic AFTER INSERT ON besu_final_receipt
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION besu_receipt_atomic_guard();
CREATE CONSTRAINT TRIGGER besu_terminal_atomic AFTER UPDATE ON besu_signed_submission
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION besu_receipt_atomic_guard();
