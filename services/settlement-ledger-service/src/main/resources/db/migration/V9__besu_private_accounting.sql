-- ADR-016 additive private control accounts. Public journal rows and mappings are untouched.
ALTER TABLE ledger_account DROP CONSTRAINT ledger_account_asset_check;
ALTER TABLE ledger_account ADD CONSTRAINT ledger_account_asset_check CHECK (asset IN ('USD','USDC','ETH','INR','DEMOUSD','BESU_GAS'));
ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_asset_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_asset_check CHECK (asset IN ('USD','USDC','ETH','INR','DEMOUSD','BESU_GAS'));
INSERT INTO ledger_account(ledger_account_id,account_code,account_name,account_type,asset,is_active) VALUES
(gen_random_uuid(),'150001','Private DEMOUSD Treasury Control','ASSET','DEMOUSD',true),
(gen_random_uuid(),'150002','Private DEMOUSD In Transit','ASSET','DEMOUSD',true),
(gen_random_uuid(),'150003','Private DEMOUSD Delivered Control','ASSET','DEMOUSD',true),
(gen_random_uuid(),'250001','Simulated DEMOUSD Allocation Control','LIABILITY','DEMOUSD',true),
(gen_random_uuid(),'550001','Private BESU_GAS Expense','EXPENSE','BESU_GAS',true),
(gen_random_uuid(),'150004','Private BESU_GAS Clearing','ASSET','BESU_GAS',true)
ON CONFLICT(account_code) DO NOTHING;
-- Provenance on every private journal qualifies its quantities by genesis, contract and execution.
-- This acknowledgement also records complete zero-fee/reverted receipts without a zero-value journal.
CREATE TABLE besu_ledger_receipt (
 execution_id UUID PRIMARY KEY,
 payment_id VARCHAR(64) NOT NULL UNIQUE,
 correlation_id VARCHAR(64) NOT NULL,
 evidence JSONB NOT NULL,
 confirmation_journal UUID REFERENCES ledger_journal(journal_id),
 fee_journal UUID REFERENCES ledger_journal(journal_id),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK (evidence->'receipt' IS NOT NULL AND evidence->'receipt'<>'null'::jsonb)
);
CREATE FUNCTION protect_besu_ledger_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'BESU_LEDGER_RECEIPT_IMMUTABLE';
END $$;
CREATE TRIGGER besu_ledger_receipt_immutable BEFORE UPDATE OR DELETE ON besu_ledger_receipt
 FOR EACH ROW EXECUTE FUNCTION protect_besu_ledger_receipt();

-- Defend the receipt/journal binding even when an internal SQL writer bypasses Java.
CREATE FUNCTION validate_besu_ledger_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE doc jsonb; receipt jsonb; fee numeric; confirmation_ok boolean; fee_ok boolean;
BEGIN
 doc=(NEW.evidence->>'executionDocument')::jsonb;
 receipt=NEW.evidence->'receipt';
 IF doc IS NULL OR (doc->>'executionId') IS DISTINCT FROM NEW.execution_id::text
   OR (doc#>>'{review,instruction,paymentId}') IS DISTINCT FROM NEW.payment_id
   OR (doc#>>'{review,instruction,correlationId}') IS DISTINCT FROM NEW.correlation_id
   OR receipt IS NULL OR receipt='null'::jsonb
   OR (NEW.evidence->>'executionFingerprint') IS NULL THEN
  RAISE EXCEPTION 'BESU_LEDGER_RECEIPT_SCOPE_CONFLICT';
 END IF;
 fee=(receipt->>'totalFeeWei')::numeric;
 IF fee IS NULL OR fee<0 OR (receipt->>'gasUsed') IS NULL OR (receipt->>'gasPriceWei') IS NULL
   OR (receipt->>'gasUsed')::numeric<=0 OR (receipt->>'gasPriceWei')::numeric<0
   OR fee<>(receipt->>'gasUsed')::numeric*(receipt->>'gasPriceWei')::numeric
   OR (receipt->>'success') IS NULL THEN
  RAISE EXCEPTION 'BESU_LEDGER_RECEIPT_FEE_INVALID';
 END IF;
 IF ((receipt->>'success')::boolean) IS DISTINCT FROM (NEW.confirmation_journal IS NOT NULL)
   OR (fee>0) IS DISTINCT FROM (NEW.fee_journal IS NOT NULL) THEN
  RAISE EXCEPTION 'BESU_LEDGER_RECEIPT_JOURNALS_REQUIRED';
 END IF;
 IF NEW.confirmation_journal IS NOT NULL THEN
  SELECT EXISTS(SELECT 1 FROM ledger_journal j WHERE j.journal_id=NEW.confirmation_journal
   AND j.payment_id=NEW.payment_id AND j.correlation_id=NEW.correlation_id
   AND j.journal_type='BLOCKCHAIN_CONFIRMATION' AND j.status='POSTED'
   AND j.evidence->>'mode'='PRIVATE_DEMO' AND j.evidence->'privateSettlement'=NEW.evidence
   AND j.evidence->>'transactionHash'=receipt->>'transactionHash') INTO confirmation_ok;
  IF NOT confirmation_ok THEN RAISE EXCEPTION 'BESU_LEDGER_CONFIRMATION_CONFLICT'; END IF;
 END IF;
 IF NEW.fee_journal IS NOT NULL THEN
  SELECT EXISTS(SELECT 1 FROM ledger_journal j WHERE j.journal_id=NEW.fee_journal
   AND j.payment_id=NEW.payment_id AND j.correlation_id=NEW.correlation_id
   AND j.journal_type='NETWORK_FEE' AND j.status='POSTED'
   AND j.evidence->>'mode'='PRIVATE_DEMO' AND j.evidence->'privateSettlement'=NEW.evidence
   AND j.evidence->>'transactionHash'=receipt->>'transactionHash') INTO fee_ok;
  IF NOT fee_ok THEN RAISE EXCEPTION 'BESU_LEDGER_FEE_CONFLICT'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER besu_ledger_receipt_binding BEFORE INSERT ON besu_ledger_receipt
 FOR EACH ROW EXECUTE FUNCTION validate_besu_ledger_receipt();
