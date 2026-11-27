\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Kolkata';
\echo Instruction and simulated compliance: no financial execution implied
SELECT p.payment_id, p.correlation_id, a.customer_id, p.sender_account_id,
       p.beneficiary_id, b.name AS beneficiary, b.wallet_address,
       p.amount, p.source_currency, p.payment_rail, p.status, p.created_at, p.updated_at,
       p.intent_snapshot #> '{instruction}' AS confirmed_instruction
FROM payment p JOIN account a ON a.account_id = p.sender_account_id
JOIN beneficiary b ON b.beneficiary_id = p.beneficiary_id
WHERE p.payment_id = :'payment_id' AND a.customer_id = :'customer_id'::uuid;
SELECT new_status, reason, actor_service, timestamp
FROM payment_status_history WHERE payment_id = :'payment_id' ORDER BY timestamp, id;
SELECT check_type, result, simulation, reason, checked_at
FROM compliance_check WHERE payment_id = :'payment_id' ORDER BY checked_at, check_type;
SELECT event_type, status, retry_count, created_at, published_at
FROM payment_outbox_event WHERE aggregate_id = :'payment_id' ORDER BY created_at, event_id;
COMMIT;
