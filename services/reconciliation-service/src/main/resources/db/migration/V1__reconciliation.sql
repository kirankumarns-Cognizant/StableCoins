CREATE TABLE reconciliation(reconciliation_id UUID PRIMARY KEY,execution_id UUID NOT NULL,payment_id VARCHAR(50) NOT NULL,correlation_id VARCHAR(60) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('MATCHED','MISMATCH','PENDING')),source_hash VARCHAR(64) NOT NULL,document JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX reconciliation_payment_latest ON reconciliation(payment_id,created_at DESC);
CREATE TABLE reconciliation_detail(detail_id BIGSERIAL PRIMARY KEY,reconciliation_id UUID NOT NULL REFERENCES reconciliation(reconciliation_id),finding VARCHAR(100) NOT NULL);
