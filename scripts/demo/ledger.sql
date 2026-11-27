\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Kolkata';
\echo Immutable journal headers and exact debit/credit lines
SELECT journal_id, correlation_id, journal_type, status, reference, created_at, posted_at
FROM ledger_journal WHERE payment_id = :'payment_id' ORDER BY created_at, journal_type;
SELECT j.journal_type, j.status, a.account_code, a.account_name, e.asset, e.debit, e.credit
FROM ledger_entry e JOIN ledger_journal j ON j.journal_id = e.journal_id
JOIN ledger_account a ON a.ledger_account_id = e.ledger_account_id
WHERE e.payment_id = :'payment_id' ORDER BY j.created_at, j.journal_type, a.account_code;
\echo Each journal must balance PER ASSET; zero rows is not proof of completion
SELECT j.journal_id, j.journal_type, j.status, e.asset, count(e.entry_id) AS entry_count,
       sum(e.debit) AS debits, sum(e.credit) AS credits,
       count(e.entry_id) > 0 AND sum(e.debit) = sum(e.credit) AS balanced
FROM ledger_journal j LEFT JOIN ledger_entry e ON e.journal_id = j.journal_id
WHERE j.payment_id = :'payment_id' GROUP BY j.journal_id, e.asset ORDER BY j.created_at, j.journal_type;
SELECT settlement_id, journal_id, fee_journal_id, settlement_type, asset, amount,
       status, transaction_hash, created_at, settled_at
FROM settlement WHERE payment_id = :'payment_id' ORDER BY created_at;
COMMIT;
