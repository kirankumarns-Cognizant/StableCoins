\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Kolkata';
\echo Stack-wide unfinished work: review before resuming consumers or changing mode
SELECT payment_id, execution_id, status, failure_code, transaction_id, expires_at, updated_at
FROM payment_execution WHERE status NOT IN ('CONFIRMED', 'REVIEW_REQUIRED') ORDER BY updated_at;
SELECT payment_id, transaction_id, evidence_mode, status, tx_hash
FROM blockchain_transaction WHERE status <> 'CONFIRMED' ORDER BY created_at;
SELECT payment_id, phase, failure_code, updated_at
FROM payment_closure WHERE phase <> 'COMPLETED' ORDER BY updated_at;
SELECT event_type, status, count(*) AS event_count
FROM payment_outbox_event WHERE status <> 'PUBLISHED' GROUP BY event_type, status ORDER BY event_type, status;
COMMIT;
