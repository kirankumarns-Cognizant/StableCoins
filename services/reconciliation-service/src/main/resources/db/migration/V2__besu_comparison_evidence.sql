-- Only ADR-016 private comparison records acquire these guards. Public reports are unchanged.
CREATE FUNCTION protect_besu_reconciliation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP <> 'INSERT' AND OLD.document->>'kind' = 'BESU_CHAIN_LEDGER_V1' THEN
    RAISE EXCEPTION 'BESU_RECONCILIATION_IMMUTABLE';
  END IF;
  IF TG_OP <> 'DELETE' AND NEW.document->>'kind' = 'BESU_CHAIN_LEDGER_V1' THEN
    IF NEW.document->>'closureGate' IS DISTINCT FROM 'PAYMENT_OWNER_INTEGRATION_PENDING'
       OR NEW.document->>'status' IS DISTINCT FROM NEW.status
       OR jsonb_typeof(NEW.document->'findings') IS DISTINCT FROM 'array'
       OR coalesce(NEW.document->>'executionFingerprint','') !~ '^[0-9a-f]{64}$'
       OR (NEW.document->>'executionDocument')::jsonb->>'executionId' IS DISTINCT FROM NEW.execution_id::text
       OR (NEW.document->>'executionDocument')::jsonb#>>'{review,instruction,paymentId}' IS DISTINCT FROM NEW.payment_id
       OR (NEW.document->>'executionDocument')::jsonb#>>'{review,instruction,correlationId}' IS DISTINCT FROM NEW.correlation_id
       OR (NEW.status='MATCHED' AND jsonb_array_length(NEW.document->'findings')<>0)
       OR (NEW.status<>'MATCHED' AND jsonb_array_length(NEW.document->'findings')=0) THEN
      RAISE EXCEPTION 'BESU_RECONCILIATION_BINDING_REQUIRED';
    END IF;
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER besu_reconciliation_guard BEFORE INSERT OR UPDATE OR DELETE ON reconciliation
  FOR EACH ROW EXECUTE FUNCTION protect_besu_reconciliation();

CREATE FUNCTION protect_besu_reconciliation_detail() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE doc jsonb;
BEGIN
  IF TG_OP<>'INSERT' THEN
    SELECT document INTO doc FROM reconciliation WHERE reconciliation_id=OLD.reconciliation_id;
    IF doc->>'kind'='BESU_CHAIN_LEDGER_V1' THEN RAISE EXCEPTION 'BESU_RECONCILIATION_IMMUTABLE'; END IF;
  END IF;
  IF TG_OP<>'DELETE' THEN
    SELECT document INTO doc FROM reconciliation WHERE reconciliation_id=NEW.reconciliation_id;
    IF doc->>'kind'='BESU_CHAIN_LEDGER_V1' AND
       (NOT (doc->'findings' ? NEW.finding) OR EXISTS(SELECT 1 FROM reconciliation_detail WHERE reconciliation_id=NEW.reconciliation_id AND finding=NEW.finding)) THEN
      RAISE EXCEPTION 'BESU_RECONCILIATION_DETAIL_MISMATCH';
    END IF;
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER besu_reconciliation_detail_guard BEFORE INSERT OR UPDATE OR DELETE ON reconciliation_detail
  FOR EACH ROW EXECUTE FUNCTION protect_besu_reconciliation_detail();
