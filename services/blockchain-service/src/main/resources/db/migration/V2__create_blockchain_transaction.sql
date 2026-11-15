CREATE TABLE blockchain_transaction (
    transaction_id       UUID PRIMARY KEY,
    payment_id           UUID NOT NULL,
    correlation_id       UUID,
    network              VARCHAR(50) NOT NULL,
    from_address         VARCHAR(42) NOT NULL,
    to_address           VARCHAR(42) NOT NULL,
    token_contract       VARCHAR(42) NOT NULL,
    token_symbol         VARCHAR(10) NOT NULL,
    amount               NUMERIC(18,6) NOT NULL,
    amount_in_smallest   NUMERIC(36,0),
    estimated_gas        NUMERIC(36,0),
    gas_used             NUMERIC(36,0),
    effective_gas_price  NUMERIC(36,0),
    nonce                BIGINT,
    tx_hash              VARCHAR(66),
    block_number         BIGINT,
    status               VARCHAR(30) NOT NULL DEFAULT 'PREPARED',
    failure_reason       TEXT,
    explorer_url         VARCHAR(300),
    submitted_at         TIMESTAMPTZ,
    confirmed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_blockchain_tx_payment_network UNIQUE (payment_id, network),
    CONSTRAINT chk_blockchain_tx_status CHECK (status IN (
        'PREPARED','SUBMITTING','SUBMITTED','SUBMISSION_UNKNOWN',
        'CONFIRMED','FAILED','REVERTED'
    ))
);

CREATE INDEX idx_blockchain_tx_payment_id ON blockchain_transaction(payment_id);
CREATE INDEX idx_blockchain_tx_status ON blockchain_transaction(status);
CREATE INDEX idx_blockchain_tx_hash ON blockchain_transaction(tx_hash);
