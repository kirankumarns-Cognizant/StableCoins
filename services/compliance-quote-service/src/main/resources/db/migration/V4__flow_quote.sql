-- Compliance owns immutable simulation quote snapshots and validation outcomes.
CREATE TABLE compliance_flow_quote (
 request_id UUID NOT NULL, provider_id VARCHAR(64) NOT NULL, request_fingerprint CHAR(64) NOT NULL,
 status VARCHAR(30) NOT NULL CHECK(status IN ('READY','REJECTED')),
 document JSONB, failure_code VARCHAR(100), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(request_id,provider_id)
);
CREATE TABLE compliance_execution_validation (
 execution_id UUID PRIMARY KEY, status VARCHAR(30) NOT NULL CHECK(status='VALIDATED'),
 quote_id UUID NOT NULL, validated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
