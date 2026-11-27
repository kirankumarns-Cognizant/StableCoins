-- Blockchain-owned read-only evidence, separate from legacy USDC readiness.
-- No cross-service FK: owner migrations can run independently.
CREATE TABLE besu_preparation_observation (
 request_id UUID PRIMARY KEY,
 request_fingerprint VARCHAR(64) NOT NULL CHECK(request_fingerprint ~ '^[0-9a-f]{64}$'),
 document JSONB NOT NULL CHECK(jsonb_typeof(document)='object'),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE FUNCTION besu_observation_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'BESU_OBSERVATION_IMMUTABLE';
END;
$$;
CREATE TRIGGER besu_observation_no_mutation BEFORE UPDATE OR DELETE ON besu_preparation_observation
 FOR EACH ROW EXECUTE FUNCTION besu_observation_immutable();
