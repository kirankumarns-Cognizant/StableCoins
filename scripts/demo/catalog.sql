\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Kolkata';
\echo Customer accounts, configured recipients, and recent payments
SELECT account_id, currency, account_type, balance, status
FROM account WHERE customer_id = :'customer_id'::uuid ORDER BY account_id;
SELECT beneficiary_id, name, currency, wallet_address, status
FROM beneficiary WHERE customer_id = :'customer_id'::uuid ORDER BY beneficiary_id;
SELECT p.payment_id, p.amount, p.status, p.created_at,
       p.intent_snapshot #>> '{instruction,maximumFee}' AS maximum_fee_usd,
       p.intent_snapshot #>> '{instruction,deliverBy}' AS stored_deadline,
       e.execution_id, e.status AS execution_status, e.transaction_id
FROM payment p JOIN account a ON a.account_id = p.sender_account_id
LEFT JOIN payment_execution e ON e.payment_id = p.payment_id
WHERE a.customer_id = :'customer_id'::uuid
ORDER BY p.created_at DESC, p.payment_id LIMIT 30;
COMMIT;
