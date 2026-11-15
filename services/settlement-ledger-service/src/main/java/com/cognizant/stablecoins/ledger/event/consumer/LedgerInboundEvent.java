package com.cognizant.stablecoins.ledger.event.consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.cognizant.stablecoins.ledger.accounting.PostingEvidence.Mode;
import java.time.Instant;
import java.util.Set;

/** Common bounded envelope/chain validation for ledger consumers. Never logs raw event contents. */
record LedgerInboundEvent(String eventId,String paymentId,String correlationId,Instant timestamp,JsonNode payload) {
    static LedgerInboundEvent read(ObjectMapper mapper,String body,String expected,Set<String> versions) {
        try {
            if(body==null || body.length()>131072) throw new IllegalArgumentException();
            JsonNode e=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if(!expected.equals(e.path("eventType").asText()) || !versions.contains(e.path("eventVersion").asText()) || !e.path("payload").isObject())
                throw new IllegalArgumentException();
            return new LedgerInboundEvent(identifier(e.get("eventId")),identifier(e.get("paymentId")),identifier(e.get("correlationId")),Instant.parse(e.path("timestamp").asText()),e.get("payload"));
        } catch(Exception exception) { throw new IllegalArgumentException("Failed to deserialize "+expected+" message"); }
    }
    Mode chainMode() {
        long chain=payload.path("chainId").asLong(0);
        String network=payload.path("network").asText();
        boolean supported=(chain==84532 && "base-sepolia".equals(network))
                || (chain==11155111 && "ethereum-sepolia".equals(network));
        if(!supported) throw new IllegalArgumentException("UNSUPPORTED_CHAIN_EVIDENCE");
        if(!payload.path("txHash").asText().matches("0x[0-9a-fA-F]{64}") || !"USDC".equals(payload.path("tokenSymbol").asText())
                || !payload.path("amount").isNumber() || payload.path("amount").decimalValue().signum()<=0) throw new IllegalArgumentException("INVALID_CHAIN_EVIDENCE");
        return switch(payload.path("evidenceMode").asText()) { case "LIVE" -> Mode.REAL_TESTNET; case "MOCK" -> Mode.MOCK; default -> throw new IllegalArgumentException("CHAIN_EVIDENCE_MODE_REQUIRED"); };
    }
    private static String identifier(JsonNode n) {
        if(n==null || !n.isTextual() || !n.asText().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) throw new IllegalArgumentException();
        return n.asText();
    }
}
