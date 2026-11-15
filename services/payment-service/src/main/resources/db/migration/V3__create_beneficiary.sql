-- V3: BENEFICIARY table — owned by payment-service (DEC-02 / ADR-007)
CREATE TABLE beneficiary (
    beneficiary_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id    UUID         NOT NULL REFERENCES customer(customer_id),
    name           VARCHAR(255) NOT NULL,
    country        VARCHAR(3)   NOT NULL,
    currency       VARCHAR(3)   NOT NULL,
    wallet_address VARCHAR(255),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);
