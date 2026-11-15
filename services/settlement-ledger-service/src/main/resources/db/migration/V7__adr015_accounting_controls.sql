-- Accepted ADR-015. Do not rewrite previously posted financial data.
ALTER TABLE ledger_account DROP CONSTRAINT ledger_account_asset_check;
ALTER TABLE ledger_account ADD CONSTRAINT ledger_account_asset_check CHECK (asset IN ('USD','USDC','ETH','INR'));
ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_asset_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_asset_check CHECK (asset IN ('USD','USDC','ETH','INR'));
ALTER TABLE ledger_entry ALTER COLUMN debit TYPE NUMERIC(38,18), ALTER COLUMN credit TYPE NUMERIC(38,18);
ALTER TABLE ledger_journal ALTER COLUMN journal_type TYPE VARCHAR(100);
ALTER TABLE ledger_journal ADD COLUMN evidence JSONB, ADD COLUMN posting_fingerprint VARCHAR(64);
CREATE TABLE ledger_payment_lock (payment_id VARCHAR(64) PRIMARY KEY, correlation_id VARCHAR(64) NOT NULL);
-- Existing inconsistent correlations must be investigated, not silently normalized.
DO $$ BEGIN
 IF EXISTS (SELECT payment_id FROM ledger_journal GROUP BY payment_id HAVING count(DISTINCT correlation_id) > 1) THEN
  RAISE EXCEPTION 'LEDGER_CORRELATION_REVIEW_REQUIRED';
 END IF;
END $$;
INSERT INTO ledger_payment_lock(payment_id,correlation_id) SELECT DISTINCT payment_id,correlation_id FROM ledger_journal;
INSERT INTO ledger_account (ledger_account_id,account_code,account_name,account_type,asset,is_active) VALUES
(gen_random_uuid(),'110003','Simulated USD Conversion Control','ASSET','USD',true),
(gen_random_uuid(),'220001','Simulated USDC Allocation Control','LIABILITY','USDC',true),
(gen_random_uuid(),'130001','ETH Network Fee Clearing','ASSET','ETH',true),
(gen_random_uuid(),'110004','Simulated USD Payout Provider Receivable','ASSET','USD',true),
(gen_random_uuid(),'140001','Simulated INR Payout Provider Receivable','ASSET','INR',true),
(gen_random_uuid(),'240001','Simulated INR Beneficiary Payable','LIABILITY','INR',true)
ON CONFLICT (account_code) DO NOTHING;

-- Financial lines can only be inserted while their own journal is pending.
CREATE FUNCTION protect_ledger_entry() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE journal_state text; journal_payment text; account_asset text; account_active boolean;
BEGIN
 IF TG_OP <> 'INSERT' THEN RAISE EXCEPTION 'LEDGER_ENTRY_IMMUTABLE'; END IF;
 SELECT status,payment_id INTO journal_state,journal_payment FROM ledger_journal WHERE journal_id=NEW.journal_id FOR SHARE;
 IF NEW.payment_id IS DISTINCT FROM journal_payment THEN RAISE EXCEPTION 'LEDGER_PAYMENT_SCOPE_MISMATCH'; END IF;
 IF journal_state IS DISTINCT FROM 'PENDING' THEN RAISE EXCEPTION 'LEDGER_JOURNAL_NOT_PENDING'; END IF;
 SELECT asset,is_active INTO account_asset,account_active FROM ledger_account WHERE ledger_account_id=NEW.ledger_account_id FOR SHARE;
 IF account_active IS DISTINCT FROM true THEN RAISE EXCEPTION 'LEDGER_ACCOUNT_INACTIVE'; END IF;
 IF account_asset IS DISTINCT FROM NEW.asset THEN RAISE EXCEPTION 'LEDGER_ACCOUNT_ASSET_MISMATCH'; END IF;
 IF NOT ((NEW.debit>0 AND NEW.credit=0) OR (NEW.credit>0 AND NEW.debit=0)) THEN
  RAISE EXCEPTION 'LEDGER_ENTRY_REQUIRES_ONE_POSITIVE_SIDE';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ledger_entry_immutable BEFORE INSERT OR UPDATE OR DELETE ON ledger_entry FOR EACH ROW EXECUTE FUNCTION protect_ledger_entry();

CREATE FUNCTION protect_ledger_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'PENDING' OR NEW.posted_at IS NOT NULL THEN RAISE EXCEPTION 'LEDGER_JOURNAL_MUST_START_PENDING'; END IF;
  RETURN NEW;
 END IF;
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'LEDGER_JOURNAL_IMMUTABLE'; END IF;
 IF OLD.status='PENDING' THEN
  IF NEW.status<>'POSTED' OR NEW.posted_at IS NULL OR
     NOT EXISTS (SELECT 1 FROM ledger_entry WHERE journal_id=OLD.journal_id) OR
     EXISTS (SELECT asset FROM ledger_entry WHERE journal_id=OLD.journal_id GROUP BY asset HAVING sum(debit)<>sum(credit)) THEN
   RAISE EXCEPTION 'LEDGER_JOURNAL_NOT_BALANCED';
  END IF;
  IF (to_jsonb(NEW)-ARRAY['status','posted_at']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','posted_at']) THEN
   RAISE EXCEPTION 'LEDGER_JOURNAL_IMMUTABLE';
  END IF;
 ELSIF OLD.status='POSTED' THEN
  IF NEW.status<>'REVERSED' OR NEW.reversed_by_journal_id IS NULL OR
    (to_jsonb(NEW)-ARRAY['status','reversed_by_journal_id']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','reversed_by_journal_id']) THEN
   RAISE EXCEPTION 'LEDGER_JOURNAL_IMMUTABLE';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM ledger_journal r WHERE r.journal_id=NEW.reversed_by_journal_id AND r.status='POSTED'
      AND r.payment_id=OLD.payment_id AND r.correlation_id=OLD.correlation_id AND r.evidence IS NOT DISTINCT FROM OLD.evidence)
    OR (SELECT count(*) FROM ledger_entry WHERE journal_id=OLD.journal_id) <>
       (SELECT count(*) FROM ledger_entry WHERE journal_id=NEW.reversed_by_journal_id)
    OR EXISTS (SELECT 1 FROM ledger_entry o WHERE o.journal_id=OLD.journal_id AND NOT EXISTS
      (SELECT 1 FROM ledger_entry r WHERE r.journal_id=NEW.reversed_by_journal_id AND r.reversal_of=o.entry_id
       AND r.asset=o.asset AND r.ledger_account_id=o.ledger_account_id AND r.debit=o.credit AND r.credit=o.debit)) THEN
   RAISE EXCEPTION 'LEDGER_REVERSAL_EVIDENCE_REQUIRED';
  END IF;
 ELSE RAISE EXCEPTION 'LEDGER_JOURNAL_IMMUTABLE';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ledger_journal_immutable BEFORE INSERT OR UPDATE OR DELETE ON ledger_journal FOR EACH ROW EXECUTE FUNCTION protect_ledger_journal();

CREATE FUNCTION protect_used_ledger_account() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS (SELECT 1 FROM ledger_entry WHERE ledger_account_id=OLD.ledger_account_id) THEN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'LEDGER_ACCOUNT_IN_USE'; END IF;
  IF ROW(OLD.account_code,OLD.account_type,OLD.asset) IS DISTINCT FROM ROW(NEW.account_code,NEW.account_type,NEW.asset) THEN
   RAISE EXCEPTION 'LEDGER_ACCOUNT_CLASSIFICATION_IMMUTABLE';
  END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ledger_used_account_immutable BEFORE UPDATE OR DELETE ON ledger_account FOR EACH ROW EXECUTE FUNCTION protect_used_ledger_account();
