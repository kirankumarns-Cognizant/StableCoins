CREATE TABLE blockchain_flow_observation (
 request_id UUID PRIMARY KEY, request_fingerprint CHAR(64) NOT NULL,
 status VARCHAR(30) NOT NULL CHECK(status='READY'), document JSONB NOT NULL,
 failure_code VARCHAR(100), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
