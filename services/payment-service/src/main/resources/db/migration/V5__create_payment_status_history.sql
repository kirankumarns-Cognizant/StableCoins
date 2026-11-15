-- V5: PAYMENT_STATUS_HISTORY — auditable record of every state transition.
CREATE TABLE payment_status_history (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id   VARCHAR(50)  NOT NULL REFERENCES payment(payment_id),
    old_status   VARCHAR(40),
    new_status   VARCHAR(40)  NOT NULL,
    reason       VARCHAR(500),
    actor_service VARCHAR(100) NOT NULL,
    timestamp    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_psh_payment_id ON payment_status_history(payment_id);
