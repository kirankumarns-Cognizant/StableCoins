-- Explicitly simulated payouts. Business binding is immutable; status is audited.
CREATE TABLE visa_transaction (
 payout_id UUID PRIMARY KEY, execution_id UUID NOT NULL UNIQUE, payment_id VARCHAR(50) NOT NULL UNIQUE,
 correlation_id VARCHAR(60) NOT NULL,provider_id VARCHAR(64) NOT NULL,currency VARCHAR(3) NOT NULL CHECK(currency IN ('USD','INR')),
 amount NUMERIC(24,2) NOT NULL CHECK(amount>0),status VARCHAR(20) NOT NULL CHECK(status IN ('ACCEPTED','PROCESSING','SUCCEEDED','FAILED','UNKNOWN')),
 reference VARCHAR(100) NOT NULL UNIQUE,reservation_id UUID REFERENCES visa_liquidity_reservation(reservation_id),
 quote_id UUID NOT NULL,quote_version INTEGER NOT NULL,transaction_hash VARCHAR(66) NOT NULL,
 evidence_mode VARCHAR(20) NOT NULL CHECK(evidence_mode='SIMULATED'),scenario VARCHAR(20) NOT NULL CHECK(scenario IN ('SUCCEEDED','DECLINED','TIMEOUT')),
 resolution_id UUID UNIQUE,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE visa_payout_audit(audit_id BIGSERIAL PRIMARY KEY,payout_id UUID NOT NULL REFERENCES visa_transaction(payout_id),
 status VARCHAR(20) NOT NULL,evidence_mode VARCHAR(20) NOT NULL DEFAULT 'SIMULATED',timestamp TIMESTAMPTZ NOT NULL DEFAULT now());
