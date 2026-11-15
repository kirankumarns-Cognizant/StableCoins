-- Run after seed-wallet-demo.sql, only in the dedicated fictional POC database.
\set ON_ERROR_STOP on
SELECT :'supplier_wallet' ~ '^0x[0-9a-fA-F]{40}$' AND lower(:'supplier_wallet') <> '0x0000000000000000000000000000000000000000' AS wallet_valid \gset
\if :wallet_valid
BEGIN;
INSERT INTO beneficiary(beneficiary_id,customer_id,name,country,currency,wallet_address,status)
 VALUES ('44444444-4444-4444-4444-444444444444','11111111-1111-1111-1111-111111111111','Fictional INR Demo Supplier','IND','INR',lower(:'supplier_wallet'),'ACTIVE') ON CONFLICT DO NOTHING;
SELECT wallet_address=lower(:'supplier_wallet') AND currency='INR' AND customer_id='11111111-1111-1111-1111-111111111111' AND status='ACTIVE' AS wallet_matches FROM beneficiary WHERE beneficiary_id='44444444-4444-4444-4444-444444444444' \gset
\if :wallet_matches
COMMIT;
\else
ROLLBACK;
\echo 'Existing beneficiary binding differs; investigate instead of changing an approved recipient.'
\quit 1
\endif
\else
\echo 'Supply an explicit non-zero beneficiary testnet wallet.'
\quit 1
\endif
