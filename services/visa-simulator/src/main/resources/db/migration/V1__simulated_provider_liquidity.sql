-- SIMULATED fiat only. No bank account balances or Visa commercial connection.
CREATE TABLE visa_liquidity_pocket (
 pocket_id VARCHAR(100) PRIMARY KEY, provider_id VARCHAR(64) NOT NULL, currency VARCHAR(3) NOT NULL,
 total NUMERIC(24,2) NOT NULL CHECK(total>=0), UNIQUE(provider_id,currency), CHECK(currency IN ('USD','INR'))
);
CREATE TABLE visa_liquidity_funding (
 operation_id VARCHAR(64) PRIMARY KEY, pocket_id VARCHAR(100) NOT NULL REFERENCES visa_liquidity_pocket(pocket_id),
 amount NUMERIC(24,2) NOT NULL CHECK(amount>0), timestamp TIMESTAMPTZ NOT NULL
);
CREATE TABLE visa_liquidity_reservation (
 reservation_id UUID PRIMARY KEY, payment_id VARCHAR(64) NOT NULL UNIQUE, correlation_id VARCHAR(64) NOT NULL,
 pocket_id VARCHAR(100) NOT NULL REFERENCES visa_liquidity_pocket(pocket_id), provider_id VARCHAR(64) NOT NULL,
 currency VARCHAR(3) NOT NULL CHECK(currency IN ('USD','INR')), amount NUMERIC(24,2) NOT NULL CHECK(amount>0),
 status VARCHAR(20) NOT NULL CHECK(status IN ('HELD','PINNED','RELEASED','CONSUMED','EXPIRED')),
 expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_visa_reservation_pocket ON visa_liquidity_reservation(pocket_id,status);
CREATE TABLE visa_liquidity_audit (
 audit_id UUID PRIMARY KEY, reservation_id UUID NOT NULL REFERENCES visa_liquidity_reservation(reservation_id),
 payment_id VARCHAR(64) NOT NULL, correlation_id VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL, timestamp TIMESTAMPTZ NOT NULL
);

ALTER TABLE visa_liquidity_pocket ADD CONSTRAINT chk_simulated_pocket_identity
 CHECK(pocket_id=provider_id || ':' || currency);
ALTER TABLE visa_liquidity_reservation ADD CONSTRAINT chk_simulated_reservation_identity
 CHECK(pocket_id=provider_id || ':' || currency);
