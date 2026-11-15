-- V1: CUSTOMER table — owned by payment-service (DEC-02 / ADR-007)
CREATE TABLE customer (
    customer_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    customer_type VARCHAR(20)  NOT NULL CHECK (customer_type IN ('CORPORATE', 'INDIVIDUAL')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
