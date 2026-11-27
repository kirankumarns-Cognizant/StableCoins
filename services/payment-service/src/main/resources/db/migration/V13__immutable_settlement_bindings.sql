-- ADR-016: additive owner records. No backfill or inference from current network defaults.
-- These snapshots do not grant approvals, create reservations or authorize a transfer.
CREATE TABLE payment_settlement_binding (
    binding_id UUID PRIMARY KEY,
    phase VARCHAR(12) NOT NULL CHECK (phase IN ('INSTRUCTION','REVIEW','EXECUTION')),
    payment_id VARCHAR(50) NOT NULL REFERENCES payment(payment_id),
    correlation_id VARCHAR(60) NOT NULL,
    parent_id UUID,
    parent_phase VARCHAR(12),
    recommendation_id UUID REFERENCES routing_recommendation(recommendation_id),
    preparation_id UUID REFERENCES payment_flow_preparation(request_id),
    execution_id UUID REFERENCES payment_execution(execution_id),
    document JSONB NOT NULL CHECK (jsonb_typeof(document) = 'object'),
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (binding_id, phase, payment_id, correlation_id),
    FOREIGN KEY (parent_id, parent_phase, payment_id, correlation_id)
        REFERENCES payment_settlement_binding(binding_id, phase, payment_id, correlation_id),
    CHECK (
        (phase = 'INSTRUCTION' AND parent_id IS NULL AND parent_phase IS NULL
          AND recommendation_id IS NULL AND preparation_id IS NULL AND execution_id IS NULL)
        OR
        (phase = 'REVIEW' AND parent_id IS NOT NULL AND parent_phase IS NOT NULL AND parent_phase = 'INSTRUCTION'
          AND recommendation_id IS NOT NULL AND recommendation_id = binding_id AND preparation_id IS NOT NULL AND execution_id IS NULL)
        OR
        (phase = 'EXECUTION' AND parent_id IS NOT NULL AND parent_id = recommendation_id AND parent_phase IS NOT NULL AND parent_phase = 'REVIEW'
          AND recommendation_id IS NOT NULL AND preparation_id IS NOT NULL AND execution_id IS NOT NULL AND execution_id = binding_id)
    )
);
CREATE UNIQUE INDEX settlement_one_instruction ON payment_settlement_binding(payment_id)
    WHERE phase = 'INSTRUCTION';
CREATE UNIQUE INDEX settlement_one_execution ON payment_settlement_binding(payment_id)
    WHERE phase = 'EXECUTION';
CREATE INDEX settlement_payment_history ON payment_settlement_binding(payment_id, captured_at);

CREATE FUNCTION payment_settlement_binding_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'SETTLEMENT_BINDING_IMMUTABLE';
END;
$$;
CREATE TRIGGER payment_settlement_binding_no_mutation
    BEFORE UPDATE OR DELETE ON payment_settlement_binding
    FOR EACH ROW EXECUTE FUNCTION payment_settlement_binding_immutable();
