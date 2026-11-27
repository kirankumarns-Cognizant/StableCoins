-- Additive payment-owner failed-transfer and explicit recovery authority. No refund is inferred.
CREATE TABLE payment_besu_failure (
 execution_id UUID PRIMARY KEY, payment_id VARCHAR(50) NOT NULL UNIQUE, correlation_id VARCHAR(60) NOT NULL,
 execution_document JSONB NOT NULL, execution_fingerprint VARCHAR(64) NOT NULL CHECK(execution_fingerprint ~ '^[0-9a-f]{64}$'),
 source_hash VARCHAR(64) NOT NULL CHECK(source_hash ~ '^[0-9a-f]{64}$'), tx_hash VARCHAR(66) NOT NULL CHECK(tx_hash ~ '^0x[0-9a-f]{64}$'),
 failure_code VARCHAR(64) NOT NULL DEFAULT 'BESU_TRANSACTION_REVERTED' CHECK(failure_code='BESU_TRANSACTION_REVERTED'),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(execution_id,source_hash),
 FOREIGN KEY(execution_id,payment_id,correlation_id) REFERENCES payment_besu_funding(execution_id,payment_id,correlation_id)
);
CREATE TABLE payment_besu_recovery_authorization (
 execution_id UUID PRIMARY KEY, payment_id VARCHAR(50) NOT NULL, correlation_id VARCHAR(60) NOT NULL,
 execution_document JSONB NOT NULL, execution_fingerprint VARCHAR(64) NOT NULL CHECK(execution_fingerprint ~ '^[0-9a-f]{64}$'),
 source_hash VARCHAR(64) NOT NULL, actor_id VARCHAR(255) NOT NULL CHECK(length(actor_id)>0),
 action VARCHAR(64) NOT NULL CHECK(action='RELEASE_REVERTED_TOKEN_IN_FLIGHT'), authorized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(execution_id,source_hash) REFERENCES payment_besu_failure(execution_id,source_hash)
);
CREATE FUNCTION validate_besu_failure_owner() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM payment_besu_funding f JOIN payment p ON p.payment_id=f.payment_id
  WHERE f.execution_id=NEW.execution_id AND f.payment_id=NEW.payment_id AND f.correlation_id=NEW.correlation_id
   AND f.execution_document=NEW.execution_document AND f.execution_fingerprint=NEW.execution_fingerprint AND p.status='FAILED') THEN
  RAISE EXCEPTION 'BESU_FAILED_OWNER_EVIDENCE_REQUIRED';
 END IF;
 IF TG_TABLE_NAME='payment_besu_recovery_authorization' THEN
  IF EXISTS(SELECT 1 FROM payment_besu_funding WHERE execution_id=NEW.execution_id AND actor_id=NEW.actor_id) THEN
   RAISE EXCEPTION 'BESU_RECOVERY_INDEPENDENT_APPROVER_REQUIRED';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payment_besu_failure_guard BEFORE INSERT ON payment_besu_failure FOR EACH ROW EXECUTE FUNCTION validate_besu_failure_owner();
CREATE TRIGGER payment_besu_recovery_guard BEFORE INSERT ON payment_besu_recovery_authorization FOR EACH ROW EXECUTE FUNCTION validate_besu_failure_owner();
CREATE TRIGGER payment_besu_failure_immutable BEFORE UPDATE OR DELETE ON payment_besu_failure FOR EACH ROW EXECUTE FUNCTION payment_settlement_binding_immutable();
CREATE TRIGGER payment_besu_recovery_immutable BEFORE UPDATE OR DELETE ON payment_besu_recovery_authorization FOR EACH ROW EXECUTE FUNCTION payment_settlement_binding_immutable();
