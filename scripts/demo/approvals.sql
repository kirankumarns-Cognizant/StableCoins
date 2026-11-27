\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Kolkata';
\echo Acceptance and independent approvals, grouped by exact review version
SELECT r.recommendation_id, r.version, r.state, r.required_approvals,
       count(DISTINCT d.actor_id) FILTER (WHERE d.action = 'QUOTE_ACCEPTED') AS accepting_actors,
       count(DISTINCT d.actor_id) FILTER (WHERE d.action = 'APPROVED') AS approving_actors,
       r.expires_at, r.expires_at > now() AS unexpired
FROM routing_recommendation r LEFT JOIN routing_decision d ON d.recommendation_id = r.recommendation_id
WHERE r.payment_id = :'payment_id' GROUP BY r.recommendation_id ORDER BY r.version DESC;
SELECT r.version, d.actor_id, d.action, d.decided_at
FROM routing_decision d JOIN routing_recommendation r ON r.recommendation_id = d.recommendation_id
WHERE r.payment_id = :'payment_id' ORDER BY r.version, d.decided_at, d.id;
COMMIT;
