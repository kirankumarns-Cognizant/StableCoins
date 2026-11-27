\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Kolkata';
\echo Closure, owner reconciliation reports and explicitly simulated payout
SELECT execution_id, phase, failure_code, updated_at FROM payment_closure WHERE payment_id = :'payment_id';
SELECT reconciliation_id, execution_id, correlation_id, status, source_hash, created_at, document
FROM reconciliation WHERE payment_id = :'payment_id' ORDER BY created_at DESC;
SELECT r.reconciliation_id, d.finding
FROM reconciliation r JOIN reconciliation_detail d ON d.reconciliation_id = r.reconciliation_id
WHERE r.payment_id = :'payment_id' ORDER BY r.created_at DESC, d.detail_id;
SELECT payout_id, execution_id, currency, amount, status, evidence_mode, reference, transaction_hash, updated_at
FROM visa_transaction WHERE payment_id = :'payment_id';
SELECT new_status, reason, actor_service, timestamp
FROM payment_status_history WHERE payment_id = :'payment_id' ORDER BY timestamp, id;
COMMIT;
