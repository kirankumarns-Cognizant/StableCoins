package com.cognizant.stablecoins.payment.intent;

import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import java.time.Instant;
import java.util.UUID;

/** Stored atomically with PAYMENT; versions and policy cannot drift on replay. */
public record PaymentIntentSnapshot(UUID intentId, int intentVersion, UUID customerId,
        String confirmedBy, Instant confirmedAt, SettlementAsset settlementAsset,
        boolean fundingSimulated, boolean fiatPayoutSimulated,
        PaymentIntentRequest instruction, CustomerRoutingPolicy policy) {
}
