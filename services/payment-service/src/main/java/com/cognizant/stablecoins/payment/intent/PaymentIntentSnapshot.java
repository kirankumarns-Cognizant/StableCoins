package com.cognizant.stablecoins.payment.intent;

import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import java.time.Instant;
import java.util.UUID;

/** Stored atomically with PAYMENT; versions and policy cannot drift on replay. */
public record PaymentIntentSnapshot(UUID intentId, int intentVersion, UUID customerId,
        String confirmedBy, Instant confirmedAt, SettlementAsset settlementAsset,
        boolean fundingSimulated, boolean fiatPayoutSimulated,
        PaymentIntentRequest instruction, CustomerRoutingPolicy policy,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        com.cognizant.stablecoins.payment.settlement.ConfirmedSettlement settlement) {
    public PaymentIntentSnapshot(UUID intentId, int intentVersion, UUID customerId, String confirmedBy,
            Instant confirmedAt, SettlementAsset settlementAsset, boolean fundingSimulated, boolean fiatPayoutSimulated,
            PaymentIntentRequest instruction, CustomerRoutingPolicy policy) {
        this(intentId, intentVersion, customerId, confirmedBy, confirmedAt, settlementAsset,
                fundingSimulated, fiatPayoutSimulated, instruction, policy, null);
    }
}
