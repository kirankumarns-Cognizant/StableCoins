-- Receipt-derived evidence. Existing rows remain explicitly incomplete.
ALTER TABLE blockchain_transaction ADD COLUMN additional_fee_wei NUMERIC(78,0), ADD COLUMN total_fee_wei NUMERIC(78,0),
 ADD COLUMN fee_components_complete BOOLEAN NOT NULL DEFAULT false, ADD COLUMN fee_evidence_reason VARCHAR(64),
 ADD COLUMN confirmation_count INTEGER NOT NULL DEFAULT 0;
