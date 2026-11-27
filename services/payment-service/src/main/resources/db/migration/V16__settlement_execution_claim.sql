-- Private owner claims are separate from legacy USDC payment_execution and V13 legacy FK bindings.
-- A claim pins approved material. It cannot reserve liquidity, sign or submit by itself.
ALTER TABLE routing_recommendation ADD CONSTRAINT settlement_recommendation_scope
 UNIQUE(recommendation_id,payment_id,correlation_id,customer_id,version);
ALTER TABLE payment_settlement_quote ADD CONSTRAINT settlement_quote_execution_scope
 UNIQUE(review_id,quote_id,request_id,instruction_id,payment_id,correlation_id);
CREATE TABLE payment_settlement_execution_claim (
 execution_id UUID PRIMARY KEY,
 payment_id VARCHAR(50) NOT NULL UNIQUE REFERENCES payment(payment_id),
 correlation_id VARCHAR(60) NOT NULL,
 customer_id UUID NOT NULL,
 instruction_id UUID NOT NULL,
 recommendation_id UUID NOT NULL UNIQUE,
 recommendation_version INTEGER NOT NULL CHECK(recommendation_version>0),
 quote_id UUID NOT NULL,
 quote_version INTEGER NOT NULL CHECK(quote_version>0),
 preparation_id UUID NOT NULL,
 actor_id VARCHAR(255) NOT NULL,
 claimed_at TIMESTAMPTZ NOT NULL,
 document JSONB NOT NULL CHECK(jsonb_typeof(document)='object'),
 fingerprint VARCHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 FOREIGN KEY(recommendation_id,payment_id,correlation_id,customer_id,recommendation_version)
 REFERENCES routing_recommendation(recommendation_id,payment_id,correlation_id,customer_id,version),
 FOREIGN KEY(recommendation_id,quote_id,preparation_id,instruction_id,payment_id,correlation_id)
 REFERENCES payment_settlement_quote(review_id,quote_id,request_id,instruction_id,payment_id,correlation_id)
);
CREATE TRIGGER settlement_execution_claim_no_mutation BEFORE UPDATE OR DELETE ON payment_settlement_execution_claim
 FOR EACH ROW EXECUTE FUNCTION payment_settlement_binding_immutable();