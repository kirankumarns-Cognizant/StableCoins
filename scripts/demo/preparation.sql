\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Kolkata';
\echo Versioned preparation and captured evidence (historical snapshots, not current balances)
SELECT request_id, status, recommendation_id, failure_code, created_at
FROM payment_flow_preparation WHERE payment_id = :'payment_id' ORDER BY created_at DESC;
SELECT r.recommendation_id, r.version, r.state, r.required_approvals, r.created_at, r.expires_at,
       r.expires_at > now() AS unexpired,
       r.document->'assessment' AS assessment, r.document->'quote' AS reviewed_quote
FROM routing_recommendation r WHERE r.payment_id = :'payment_id' ORDER BY r.version DESC;
SELECT q.request_id, q.provider_id, q.status, q.failure_code, q.document AS simulated_quote
FROM compliance_flow_quote q JOIN payment_flow_preparation p ON p.request_id = q.request_id
WHERE p.payment_id = :'payment_id' ORDER BY p.created_at DESC, q.provider_id;
SELECT t.request_id, t.status, t.failure_code, t.document AS captured_treasury
FROM blockchain_flow_observation t JOIN payment_flow_preparation p ON p.request_id = t.request_id
WHERE p.payment_id = :'payment_id' ORDER BY p.created_at DESC;
COMMIT;
