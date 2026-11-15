-- V2: ACCOUNT table — customer payment account, owned by payment-service (DEC-05 / ADR-012)
-- NOT to be confused with LEDGER_ACCOUNT (settlement-ledger-service).
CREATE TABLE account (
    account_id   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID           NOT NULL REFERENCES customer(customer_id),
    currency     VARCHAR(3)     NOT NULL,
    account_type VARCHAR(50)    NOT NULL,
    balance      NUMERIC(18,2)  NOT NULL DEFAULT 0.00,
    status       VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);
