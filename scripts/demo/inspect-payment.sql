-- Read-only diagnostics. Supply -v payment_id=PAY-...; no payment is created or retried.
\set ON_ERROR_STOP on
BEGIN READ ONLY;

SELECT execution_id, status, failure_code, transaction_id, expires_at, updated_at
FROM payment_execution
WHERE payment_id = :'payment_id';

SELECT v.execution_id, v.status, v.validated_at
FROM compliance_execution_validation v
JOIN payment_execution e ON e.execution_id = v.execution_id
WHERE e.payment_id = :'payment_id';

SELECT payment_id, transaction_id, evidence_mode, network, chain_id, status,
       from_address, to_address, token_contract, amount,
       tx_hash, block_number, gas_used, explorer_url
FROM blockchain_transaction
WHERE payment_id = :'payment_id';

SELECT event_type, status, retry_count, created_at, published_at
FROM payment_outbox_event
WHERE aggregate_id = :'payment_id'
ORDER BY created_at DESC
LIMIT 12;

COMMIT;
