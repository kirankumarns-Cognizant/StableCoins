-- Payment owner only. Private terminal acknowledgement, separate from legacy public closure.
ALTER TABLE payment_besu_funding ADD CONSTRAINT besu_closure_funding_scope UNIQUE(execution_id,payment_id,correlation_id);
CREATE TABLE payment_besu_closure (
 execution_id UUID PRIMARY KEY,
 payment_id VARCHAR(50) NOT NULL UNIQUE REFERENCES payment(payment_id),
 correlation_id VARCHAR(60) NOT NULL,
 execution_document JSONB NOT NULL CHECK(jsonb_typeof(execution_document)='object'),
 execution_fingerprint VARCHAR(64) NOT NULL CHECK(execution_fingerprint ~ '^[0-9a-f]{64}$'),
 report_id UUID NOT NULL UNIQUE,
 source_hash VARCHAR(64) NOT NULL CHECK(source_hash ~ '^[0-9a-f]{64}$'),
 completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(execution_id,payment_id,correlation_id) REFERENCES payment_besu_funding(execution_id,payment_id,correlation_id),
 CHECK((execution_document->>'executionId') IS NOT DISTINCT FROM execution_id::text),
 CHECK((execution_document#>>'{review,instruction,paymentId}') IS NOT DISTINCT FROM payment_id),
 CHECK((execution_document#>>'{review,instruction,correlationId}') IS NOT DISTINCT FROM correlation_id)
);
CREATE TRIGGER payment_besu_closure_no_mutation BEFORE UPDATE OR DELETE ON payment_besu_closure
 FOR EACH ROW EXECUTE FUNCTION payment_settlement_binding_immutable();

-- The acknowledgement cannot commit independently of the exact funded instruction and terminal state.
CREATE FUNCTION validate_besu_payment_closure() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (
  SELECT 1 FROM payment_besu_funding f JOIN payment p ON p.payment_id=f.payment_id
  WHERE f.execution_id=NEW.execution_id AND f.payment_id=NEW.payment_id
   AND f.correlation_id=NEW.correlation_id
   AND f.execution_document=NEW.execution_document
   AND f.execution_fingerprint=NEW.execution_fingerprint AND p.status='RECONCILED'
 ) THEN
  RAISE EXCEPTION 'BESU_PAYMENT_CLOSURE_EVIDENCE_REQUIRED';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER payment_besu_closure_exact_funding BEFORE INSERT ON payment_besu_closure
 FOR EACH ROW EXECUTE FUNCTION validate_besu_payment_closure();
