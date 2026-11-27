\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Kolkata';
\echo Execution and signed-chain progress: a UUID is not a transaction hash
SELECT e.execution_id, e.recommendation_id, e.preparation_id, e.status AS execution_status,
       e.failure_code, v.status AS compliance_validation, v.validated_at,
       e.transaction_id, t.status AS blockchain_status, t.evidence_mode, t.tx_hash,
       e.created_at, e.updated_at, e.expires_at
FROM payment_execution e LEFT JOIN compliance_execution_validation v ON v.execution_id = e.execution_id
LEFT JOIN blockchain_transaction t ON t.transaction_id = e.transaction_id
WHERE e.payment_id = :'payment_id';
SELECT transaction_id, correlation_id, evidence_mode, network, chain_id, token_contract,
       from_address, to_address, amount, reservation_status, status, failure_reason,
       tx_hash, block_number, confirmation_count, gas_used, effective_gas_price,
       additional_fee_wei, total_fee_wei, fee_components_complete, fee_evidence_reason,
       submitted_at, confirmed_at, updated_at, explorer_url
FROM blockchain_transaction WHERE payment_id = :'payment_id';
SELECT event_type, status, retry_count, created_at, published_at
FROM payment_outbox_event WHERE aggregate_id = :'payment_id' ORDER BY created_at, event_id;
COMMIT;
