-- Append-only control compensation. Original funding, submission and actual gas journals remain POSTED.
CREATE TABLE besu_ledger_compensation (
 execution_id UUID PRIMARY KEY, payment_id VARCHAR(64) NOT NULL UNIQUE, correlation_id VARCHAR(64) NOT NULL,
 execution_document JSONB NOT NULL, execution_fingerprint VARCHAR(64) NOT NULL CHECK(execution_fingerprint ~ '^[0-9a-f]{64}$'),
 source_hash VARCHAR(64) NOT NULL CHECK(source_hash ~ '^[0-9a-f]{64}$'), actor_id VARCHAR(255) NOT NULL CHECK(length(actor_id)>0),
 original_journal UUID NOT NULL UNIQUE REFERENCES ledger_journal(journal_id),
 release_journal UUID NOT NULL UNIQUE REFERENCES ledger_journal(journal_id),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(), CHECK(original_journal<>release_journal)
);
CREATE FUNCTION validate_besu_compensation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM payment_besu_recovery_authorization a
  JOIN payment_besu_failure f ON f.execution_id=a.execution_id AND f.source_hash=a.source_hash
  JOIN payment p ON p.payment_id=a.payment_id
  JOIN besu_ledger_receipt r ON r.execution_id=a.execution_id
  JOIN ledger_journal o ON o.journal_id=NEW.original_journal
  JOIN ledger_journal j ON j.journal_id=NEW.release_journal
  WHERE a.execution_id=NEW.execution_id AND a.payment_id=NEW.payment_id AND a.correlation_id=NEW.correlation_id
   AND a.execution_document=NEW.execution_document AND a.execution_fingerprint=NEW.execution_fingerprint
   AND a.source_hash=NEW.source_hash AND a.actor_id=NEW.actor_id AND a.action='RELEASE_REVERTED_TOKEN_IN_FLIGHT'
   AND p.status='FAILED' AND r.confirmation_journal IS NULL AND r.evidence#>>'{receipt,success}'='false'
   AND f.tx_hash=r.evidence#>>'{receipt,transactionHash}'
   AND o.payment_id=NEW.payment_id AND o.correlation_id=NEW.correlation_id AND o.status='POSTED' AND o.journal_type='BLOCKCHAIN_SUBMISSION'
   AND j.payment_id=o.payment_id AND j.correlation_id=o.correlation_id AND j.status='POSTED' AND j.journal_type='BLOCKCHAIN_REVERT_RELEASE'
   AND j.reference='BESU-REVERT:'||o.journal_id::text AND j.evidence=o.evidence AND o.evidence->>'mode'='PRIVATE_DEMO'
   AND (o.evidence#>>'{privateSettlement,executionDocument}')::jsonb=NEW.execution_document) THEN
  RAISE EXCEPTION 'BESU_COMPENSATION_AUTHORIZED_REVERT_REQUIRED';
 END IF;
 IF EXISTS(
   (SELECT ledger_account_id,asset,debit,credit FROM ledger_entry WHERE journal_id=NEW.release_journal
    EXCEPT ALL SELECT ledger_account_id,asset,credit,debit FROM ledger_entry WHERE journal_id=NEW.original_journal)
   UNION ALL
   (SELECT ledger_account_id,asset,credit,debit FROM ledger_entry WHERE journal_id=NEW.original_journal
    EXCEPT ALL SELECT ledger_account_id,asset,debit,credit FROM ledger_entry WHERE journal_id=NEW.release_journal)
 ) THEN RAISE EXCEPTION 'BESU_COMPENSATION_ENTRIES_CONFLICT'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER besu_compensation_guard BEFORE INSERT ON besu_ledger_compensation FOR EACH ROW EXECUTE FUNCTION validate_besu_compensation();
CREATE TRIGGER besu_compensation_immutable BEFORE UPDATE OR DELETE ON besu_ledger_compensation FOR EACH ROW EXECUTE FUNCTION protect_besu_ledger_receipt();

-- Even the generic journal API cannot commit this reserved journal type without the owner acknowledgement.
CREATE FUNCTION require_besu_compensation_ack() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM besu_ledger_compensation WHERE release_journal=NEW.journal_id) THEN
  RAISE EXCEPTION 'BESU_AUTHORIZED_COMPENSATION_ACK_REQUIRED';
 END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER besu_release_requires_ack AFTER INSERT OR UPDATE ON ledger_journal
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
 WHEN(NEW.journal_type='BLOCKCHAIN_REVERT_RELEASE' AND NEW.status='POSTED')
 EXECUTE FUNCTION require_besu_compensation_ack();
