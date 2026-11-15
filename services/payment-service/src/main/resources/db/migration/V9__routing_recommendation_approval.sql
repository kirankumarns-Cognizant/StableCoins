-- payment-service owned; financial ledger tables are unchanged.
CREATE TABLE routing_recommendation (
 recommendation_id UUID PRIMARY KEY,
 payment_id VARCHAR(50) NOT NULL REFERENCES payment(payment_id),
 correlation_id VARCHAR(60) NOT NULL,
 customer_id UUID NOT NULL REFERENCES customer(customer_id),
 version INTEGER NOT NULL CHECK(version > 0),
 required_approvals INTEGER NOT NULL CHECK(required_approvals > 0),
 state VARCHAR(20) NOT NULL CHECK(state IN ('DRAFT','PROPOSED','APPROVED','REJECTED','EXPIRED','SUPERSEDED','EXECUTED')),
 document JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 UNIQUE(payment_id,version)
);
CREATE UNIQUE INDEX routing_one_active_recommendation ON routing_recommendation(payment_id)
 WHERE state IN ('DRAFT','PROPOSED','APPROVED','EXECUTED');
CREATE TABLE routing_decision (
 id UUID PRIMARY KEY,
 recommendation_id UUID NOT NULL REFERENCES routing_recommendation(recommendation_id),
 actor_id VARCHAR(255) NOT NULL,
 action VARCHAR(30) NOT NULL CHECK(action IN ('QUOTE_ACCEPTED','APPROVED','REJECTED')),
 decided_at TIMESTAMPTZ NOT NULL,
 UNIQUE(recommendation_id,actor_id,action)
);
CREATE TABLE routing_audit (
 id UUID PRIMARY KEY,
 recommendation_id UUID NOT NULL REFERENCES routing_recommendation(recommendation_id),
 payment_id VARCHAR(50) NOT NULL REFERENCES payment(payment_id),
 correlation_id VARCHAR(60) NOT NULL,
 actor_id VARCHAR(255) NOT NULL,
 action VARCHAR(40) NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX routing_audit_recommendation ON routing_audit(recommendation_id,recorded_at);
