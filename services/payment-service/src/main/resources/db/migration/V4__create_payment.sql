-- V4: PAYMENT table — owned by payment-service (DEC-02 / ADR-007)
-- request_fingerprint added to support idempotency conflict detection (Iteration 2).
-- purpose added to record payment intent per API spec.
CREATE TABLE payment (
    payment_id          VARCHAR(50)   PRIMARY KEY,
    correlation_id      VARCHAR(60)   UNIQUE NOT NULL,
    sender_account_id   UUID          NOT NULL REFERENCES account(account_id),
    beneficiary_id      UUID          NOT NULL REFERENCES beneficiary(beneficiary_id),
    amount              NUMERIC(18,2) NOT NULL,
    source_currency     VARCHAR(3)    NOT NULL,
    destination_currency VARCHAR(3)   NOT NULL,
    payment_rail        VARCHAR(30)   NOT NULL,
    purpose             VARCHAR(50)   NOT NULL,
    status              VARCHAR(40)   NOT NULL DEFAULT 'CREATED',
    idempotency_key     VARCHAR(255)  UNIQUE NOT NULL,
    request_fingerprint VARCHAR(64)   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_status    ON payment(status);
CREATE INDEX idx_payment_created_at ON payment(created_at);
