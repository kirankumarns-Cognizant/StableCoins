-- V5: Approved chart of accounts (docs/10-LEDGER.md). Idempotent via ON CONFLICT DO NOTHING.
INSERT INTO ledger_account (ledger_account_id, account_code, account_name, account_type, asset, is_active) VALUES
    (gen_random_uuid(), '110001', 'Corporate USD Control',          'ASSET',     'USD',  TRUE),
    (gen_random_uuid(), '110002', 'Stablecoin Funding Clearing',    'ASSET',     'USD',  TRUE),
    (gen_random_uuid(), '120001', 'USDC Treasury',                  'ASSET',     'USDC', TRUE),
    (gen_random_uuid(), '120002', 'USDC In Transit',                'ASSET',     'USDC', TRUE),
    (gen_random_uuid(), '120003', 'Beneficiary USDC Settlement',    'ASSET',     'USDC', TRUE),
    (gen_random_uuid(), '210001', 'Beneficiary Settlement Payable', 'LIABILITY', 'USD',  TRUE),
    (gen_random_uuid(), '410001', 'Payment Fee Revenue',            'REVENUE',   'USD',  TRUE),
    (gen_random_uuid(), '510001', 'Blockchain Gas Expense',         'EXPENSE',   'ETH',  TRUE),
    (gen_random_uuid(), '510002', 'Payment Fee Expense',            'EXPENSE',   'USD',  TRUE)
ON CONFLICT (account_code) DO NOTHING;
