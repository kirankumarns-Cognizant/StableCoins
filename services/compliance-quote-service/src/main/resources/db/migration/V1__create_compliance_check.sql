-- V1: Compliance check results (compliance-quote-service ownership, ADR-007)
-- simulation=true is ALWAYS set — compliance is never real in this POC.
CREATE TABLE compliance_check (
    check_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id     VARCHAR(50)  NOT NULL,
    correlation_id VARCHAR(60)  NOT NULL,
    check_type     VARCHAR(50)  NOT NULL
                   CHECK (check_type IN ('KYC_KYB', 'SANCTIONS', 'AML', 'WALLET_SCREENING')),
    result         VARCHAR(10)  NOT NULL
                   CHECK (result IN ('PASS', 'FAIL')),
    simulation     BOOLEAN      NOT NULL DEFAULT TRUE,
    reason         VARCHAR(255),
    checked_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_compliance_check_payment_id ON compliance_check(payment_id);
