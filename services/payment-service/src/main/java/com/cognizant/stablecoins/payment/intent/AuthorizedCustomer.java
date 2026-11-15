package com.cognizant.stablecoins.payment.intent;

import java.util.UUID;

/** Server-internal identity boundary; never deserialize this from client JSON/headers. */
public record AuthorizedCustomer(UUID customerId, String actorId) {
    public AuthorizedCustomer {
        if (customerId == null || actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("Verified customer and actor are required");
        }
    }
}
