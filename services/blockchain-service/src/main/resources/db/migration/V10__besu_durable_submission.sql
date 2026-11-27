-- Private blockchain owner only. Signed transactions are not private keys;
-- restrict database access/backups and never export/log signed_payload.
CREATE TABLE besu_signed_submission (
 execution_id UUID PRIMARY KEY REFERENCES besu_execution_reservation(execution_id),
 scope_key VARCHAR(300) NOT NULL REFERENCES besu_treasury_scope(scope_key),
 document JSONB NOT NULL CHECK(jsonb_typeof(document)='object'),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 nonce BIGINT NOT NULL CHECK(nonce>=0 AND nonce<9223372036854775807),
 gas_limit NUMERIC(78,0) NOT NULL CHECK(gas_limit>0 AND gas_limit<=30000000),
 gas_price NUMERIC(78,0) NOT NULL CHECK(gas_price>=0),
 signed_payload BYTEA NOT NULL CHECK(octet_length(signed_payload) BETWEEN 1 AND 4096),
 tx_hash VARCHAR(66) NOT NULL UNIQUE CHECK(tx_hash ~ '^0x[0-9a-f]{64}$'),
 status VARCHAR(12) NOT NULL CHECK(status IN ('SIGNED','ATTEMPTED','SUBMITTED','UNKNOWN')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(scope_key,nonce)
);
CREATE FUNCTION besu_submission_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'BESU_SIGNED_SUBMISSION_IMMUTABLE'; END IF;
 IF (NEW.execution_id,NEW.scope_key,NEW.document,NEW.fingerprint,NEW.nonce,NEW.gas_limit,
     NEW.gas_price,NEW.signed_payload,NEW.tx_hash,NEW.created_at)
    IS DISTINCT FROM
    (OLD.execution_id,OLD.scope_key,OLD.document,OLD.fingerprint,OLD.nonce,OLD.gas_limit,
     OLD.gas_price,OLD.signed_payload,OLD.tx_hash,OLD.created_at)
    OR NOT ((OLD.status='SIGNED' AND NEW.status='ATTEMPTED')
         OR (OLD.status='ATTEMPTED' AND NEW.status IN ('SUBMITTED','UNKNOWN'))
         OR (OLD.status='UNKNOWN' AND NEW.status='SUBMITTED')) THEN
   RAISE EXCEPTION 'BESU_SIGNED_SUBMISSION_IMMUTABLE';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER besu_submission_no_rewrite BEFORE UPDATE OR DELETE ON besu_signed_submission
 FOR EACH ROW EXECUTE FUNCTION besu_submission_guard();
CREATE TABLE besu_submission_audit (
 audit_id UUID PRIMARY KEY,
 execution_id UUID NOT NULL REFERENCES besu_signed_submission(execution_id),
 status VARCHAR(12) NOT NULL CHECK(status IN ('SIGNED','ATTEMPTED','SUBMITTED','UNKNOWN')),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(execution_id,status)
);
CREATE TRIGGER besu_submission_audit_immutable BEFORE UPDATE OR DELETE ON besu_submission_audit
 FOR EACH ROW EXECUTE FUNCTION besu_observation_immutable();
