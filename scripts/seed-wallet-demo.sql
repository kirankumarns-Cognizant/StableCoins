-- Explicit fictional demo setup. Execute only against the dedicated POC database.
-- Required psql variable: supplier_wallet. Never resets an existing account balance or recipient.
\set ON_ERROR_STOP on
-- Optional identifiers let the same seed serve a second fictional customer.
\if :{?customer_id}
\else
\set customer_id '11111111-1111-1111-1111-111111111111'
\endif
\if :{?account_id}
\else
\set account_id '22222222-2222-2222-2222-222222222222'
\endif
\if :{?beneficiary_id}
\else
\set beneficiary_id '33333333-3333-3333-3333-333333333333'
\endif
SELECT :'supplier_wallet' ~ '^0x[0-9a-fA-F]{40}$' AND lower(:'supplier_wallet') <> '0x0000000000000000000000000000000000000000' AS wallet_valid \gset
\if :wallet_valid
BEGIN;
INSERT INTO customer(customer_id,name,customer_type,status)
 VALUES (:'customer_id'::uuid,'Fictional Stablecoin Demo Corporate','CORPORATE','ACTIVE') ON CONFLICT DO NOTHING;
INSERT INTO account(account_id,customer_id,currency,account_type,balance,status)
 VALUES (:'account_id'::uuid,:'customer_id'::uuid,'USD','DEMO_SIMULATED_USD',100000,'ACTIVE') ON CONFLICT DO NOTHING;
INSERT INTO beneficiary(beneficiary_id,customer_id,name,country,currency,wallet_address,status)
 VALUES (:'beneficiary_id'::uuid,:'customer_id'::uuid,'Fictional Demo Supplier','USA','USD',lower(:'supplier_wallet'),'ACTIVE') ON CONFLICT DO NOTHING;
SELECT EXISTS(SELECT 1 FROM customer WHERE customer_id=:'customer_id'::uuid AND customer_type='CORPORATE' AND status='ACTIVE')
 AND EXISTS(SELECT 1 FROM account WHERE account_id=:'account_id'::uuid AND customer_id=:'customer_id'::uuid AND currency='USD' AND account_type='DEMO_SIMULATED_USD' AND status='ACTIVE')
 AND EXISTS(SELECT 1 FROM beneficiary WHERE beneficiary_id=:'beneficiary_id'::uuid AND customer_id=:'customer_id'::uuid AND currency='USD' AND wallet_address=lower(:'supplier_wallet') AND status='ACTIVE') AS bindings_match \gset
\if :bindings_match
COMMIT;
SELECT account_id,currency,balance,account_type FROM account WHERE account_id=:'account_id'::uuid;
\else
ROLLBACK;
\echo 'Existing customer, account or beneficiary has conflicting bindings; investigate instead of changing ownership or an approved recipient.'
\quit 1
\endif
\else
\echo 'Supply a non-zero beneficiary testnet wallet using -v supplier_wallet=...'
\quit 1
\endif
