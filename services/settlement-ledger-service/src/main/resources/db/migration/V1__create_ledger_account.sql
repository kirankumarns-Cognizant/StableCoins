-- V1: Chart of accounts table for settlement-ledger-service
-- Owned by settlement-ledger-service. Payment-service ACCOUNT table is separate (ADR-012).
CREATE TABLE ledger_account (
    ledger_account_id  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code       VARCHAR(10)  NOT NULL,
    account_name       VARCHAR(100) NOT NULL,
    account_type       VARCHAR(20)  NOT NULL CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    asset              VARCHAR(10)  NOT NULL CHECK (asset IN ('USD','USDC','ETH')),
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ledger_account_code UNIQUE (account_code)
);
