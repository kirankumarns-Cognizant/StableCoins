-- Payment owns preparation and execution authority. Other services may read, never mutate.
CREATE TABLE payment_flow_preparation (
 request_id UUID PRIMARY KEY, payment_id VARCHAR(50) NOT NULL REFERENCES payment(payment_id),
 correlation_id VARCHAR(60) NOT NULL, actor_id VARCHAR(255) NOT NULL,
 request_document JSONB NOT NULL, status VARCHAR(30) NOT NULL CHECK(status IN ('PENDING','READY','REVIEW_REQUIRED')),
 recommendation_id UUID REFERENCES routing_recommendation(recommendation_id), failure_code VARCHAR(100),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX payment_flow_pending ON payment_flow_preparation(created_at) WHERE status='PENDING';
CREATE TABLE payment_execution (
 execution_id UUID PRIMARY KEY, payment_id VARCHAR(50) NOT NULL UNIQUE REFERENCES payment(payment_id),
 correlation_id VARCHAR(60) NOT NULL, recommendation_id UUID NOT NULL REFERENCES routing_recommendation(recommendation_id),
 preparation_id UUID NOT NULL REFERENCES payment_flow_preparation(request_id), actor_id VARCHAR(255) NOT NULL,
 request_document JSONB NOT NULL, transaction_id UUID,
 status VARCHAR(30) NOT NULL CHECK(status IN ('PREPARING','AWAITING_LEDGER','SUBMISSION_REQUESTED','SUBMITTED','CONFIRMED','REVIEW_REQUIRED')),
 failure_code VARCHAR(100), expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX payment_execution_pending ON payment_execution(updated_at) WHERE status NOT IN ('CONFIRMED','REVIEW_REQUIRED');
