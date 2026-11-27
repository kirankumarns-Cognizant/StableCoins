-- Owner-produced exact execution projection for ADR-007 read-only consumers.
-- Existing immutable claims are deliberately not backfilled or granted new authority.
ALTER TABLE payment_settlement_execution_claim
 ADD COLUMN execution_document JSONB,
 ADD COLUMN execution_fingerprint VARCHAR(64),
 ADD CONSTRAINT settlement_claim_execution_projection CHECK (
   (execution_document IS NULL AND execution_fingerprint IS NULL)
   OR (execution_document IS NOT NULL AND execution_fingerprint IS NOT NULL
       AND jsonb_typeof(execution_document)='object'
       AND execution_fingerprint ~ '^[0-9a-f]{64}$'
       AND document ? 'execution'
       AND execution_document=document->'execution')
 );