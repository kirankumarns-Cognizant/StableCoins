CREATE TABLE blockchain_wallet (
    wallet_id        UUID PRIMARY KEY,
    wallet_address   VARCHAR(42) NOT NULL UNIQUE,
    network          VARCHAR(50) NOT NULL,
    pending_nonce    BIGINT NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blockchain_wallet_address ON blockchain_wallet(wallet_address);
