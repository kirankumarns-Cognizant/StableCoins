-- Append-only simulator observations. These are not application financial journals or chain receipts.
CREATE TABLE visa_wallet_balance_change (
 sequence_id BIGSERIAL PRIMARY KEY,
 payout_id UUID NOT NULL REFERENCES visa_wallet_payout(payout_id),
 status VARCHAR(20) NOT NULL CHECK(status IN ('PROCESSING','UNKNOWN','SUCCEEDED','FAILED')),
 document JSONB NOT NULL,
 evidence_mode VARCHAR(20) NOT NULL DEFAULT 'SIMULATED' CHECK(evidence_mode='SIMULATED'),
 UNIQUE(payout_id,status),
 CHECK(document->>'evidenceMode' IS NOT NULL AND document->>'evidenceMode'='SIMULATED'),
 CHECK(document->>'payoutId' IS NOT NULL AND document->>'payoutId'=payout_id::text),
 CHECK(document->>'status' IS NOT NULL AND document->>'status'=status)
);