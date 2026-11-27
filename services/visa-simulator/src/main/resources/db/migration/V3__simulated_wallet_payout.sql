-- Provider simulation only. No chain transactions or financial journal writes.
CREATE TABLE visa_wallet_pocket (
 pocket_id VARCHAR(64) PRIMARY KEY, provider_id VARCHAR(64) NOT NULL,
 token_document JSONB NOT NULL, total NUMERIC(28,6) NOT NULL DEFAULT 0 CHECK(total>=0),
 evidence_mode VARCHAR(20) NOT NULL DEFAULT 'SIMULATED' CHECK(evidence_mode='SIMULATED'));
CREATE TABLE visa_wallet_funding (
 operation_id UUID PRIMARY KEY, pocket_id VARCHAR(64) NOT NULL REFERENCES visa_wallet_pocket(pocket_id),
 amount NUMERIC(28,6) NOT NULL CHECK(amount>0),
 evidence_mode VARCHAR(20) NOT NULL DEFAULT 'SIMULATED' CHECK(evidence_mode='SIMULATED'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE visa_wallet_payout (
 payout_id UUID PRIMARY KEY, execution_id UUID NOT NULL UNIQUE, payment_id VARCHAR(50) NOT NULL UNIQUE,
 correlation_id VARCHAR(60) NOT NULL, pocket_id VARCHAR(64) NOT NULL REFERENCES visa_wallet_pocket(pocket_id),
 amount NUMERIC(28,6) NOT NULL CHECK(amount>0), request_document JSONB NOT NULL,
 reference VARCHAR(100) NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PROCESSING','UNKNOWN','SUCCEEDED','FAILED')),
 scenario VARCHAR(20) NOT NULL CHECK(scenario IN ('SUCCEEDED','DECLINED','TIMEOUT')),
 resolution_id UUID UNIQUE, evidence_mode VARCHAR(20) NOT NULL CHECK(evidence_mode='SIMULATED'),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL);
CREATE INDEX visa_wallet_active_capacity ON visa_wallet_payout(pocket_id) WHERE status IN ('PROCESSING','UNKNOWN');
CREATE TABLE visa_wallet_payout_audit (
 audit_id BIGSERIAL PRIMARY KEY, payout_id UUID NOT NULL REFERENCES visa_wallet_payout(payout_id),
 status VARCHAR(20) NOT NULL, evidence_mode VARCHAR(20) NOT NULL DEFAULT 'SIMULATED' CHECK(evidence_mode='SIMULATED'),
 timestamp TIMESTAMPTZ NOT NULL DEFAULT now());