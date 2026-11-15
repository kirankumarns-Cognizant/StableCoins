-- No prompts, credentials, raw provider output or private reasoning are stored.
CREATE TABLE routing_agent_run (
    run_id UUID PRIMARY KEY,
    payment_id VARCHAR(50) NOT NULL REFERENCES payment(payment_id),
    correlation_id VARCHAR(60) NOT NULL,
    customer_id UUID NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    recommendation_id UUID NOT NULL REFERENCES routing_recommendation(recommendation_id),
    idempotency_hash VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    configured_model VARCHAR(120) NOT NULL,
    configured_provider VARCHAR(20) NOT NULL,
    inference_region VARCHAR(40) NOT NULL,
    prompt_version VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK(status IN ('RUNNING','COMPLETED','FAILED','TIMED_OUT')),
    created_at TIMESTAMPTZ NOT NULL,
    deadline_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    failure_code VARCHAR(80),
    report JSONB,
    UNIQUE(payment_id,actor_id,idempotency_hash),
    CHECK(deadline_at > created_at),
    CHECK((status='RUNNING' AND finished_at IS NULL AND report IS NULL AND failure_code IS NULL)
       OR (status='COMPLETED' AND finished_at IS NOT NULL AND report IS NOT NULL AND failure_code IS NULL)
       OR (status IN ('FAILED','TIMED_OUT') AND finished_at IS NOT NULL AND report IS NULL AND failure_code IS NOT NULL))
);
CREATE UNIQUE INDEX uq_routing_agent_active_payment ON routing_agent_run(payment_id) WHERE status='RUNNING';
CREATE INDEX ix_routing_agent_customer ON routing_agent_run(customer_id,created_at DESC);
