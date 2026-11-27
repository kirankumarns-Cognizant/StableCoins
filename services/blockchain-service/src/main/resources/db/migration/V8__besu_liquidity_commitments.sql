-- Blockchain owner only. No legacy USDC/ETH tables or financial journals changed.
CREATE TABLE besu_treasury_scope (
 scope_key VARCHAR(300) PRIMARY KEY
);
CREATE TABLE besu_liquidity_commitment (
 execution_id UUID PRIMARY KEY,
 payment_id VARCHAR(50) NOT NULL UNIQUE,
 scope_key VARCHAR(300) NOT NULL REFERENCES besu_treasury_scope(scope_key),
 token_contract VARCHAR(42) NOT NULL CHECK(token_contract ~ '^0x[0-9a-f]{40}$'),
 document JSONB NOT NULL CHECK(jsonb_typeof(document)='object'),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 status VARCHAR(10) NOT NULL CHECK(status IN ('HELD','PINNED','RELEASED')),
 expires_at TIMESTAMPTZ NOT NULL,
 pinned_at TIMESTAMPTZ,
 CHECK((status='PINNED')=(pinned_at IS NOT NULL))
);
CREATE INDEX besu_commitment_scope ON besu_liquidity_commitment(scope_key,status);
CREATE FUNCTION besu_commitment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'BESU_COMMITMENT_IMMUTABLE'; END IF;
 IF (NEW.execution_id,NEW.payment_id,NEW.scope_key,NEW.token_contract,NEW.document,NEW.fingerprint,NEW.expires_at)
    IS DISTINCT FROM
    (OLD.execution_id,OLD.payment_id,OLD.scope_key,OLD.token_contract,OLD.document,OLD.fingerprint,OLD.expires_at)
    OR OLD.status<>'HELD' OR NEW.status NOT IN ('PINNED','RELEASED') THEN
   RAISE EXCEPTION 'BESU_COMMITMENT_IMMUTABLE';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER besu_commitment_no_rewrite BEFORE UPDATE OR DELETE ON besu_liquidity_commitment
 FOR EACH ROW EXECUTE FUNCTION besu_commitment_guard();
CREATE TABLE besu_preparation_availability (
 request_id UUID PRIMARY KEY REFERENCES besu_preparation_observation(request_id),
 document JSONB NOT NULL CHECK(jsonb_typeof(document)='object'),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER besu_availability_no_mutation BEFORE UPDATE OR DELETE ON besu_preparation_availability
 FOR EACH ROW EXECUTE FUNCTION besu_observation_immutable();
