package com.cognizant.stablecoins.payment.exception;

/**
 * Raised when the same Idempotency-Key is reused with a different request payload.
 * The client must not silently receive the original response — this is an error.
 */
public class IdempotencyConflictException extends PaymentException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("IDEMPOTENCY_CONFLICT",
              "Idempotency-Key '" + idempotencyKey + "' was previously used with a different request payload");
    }
}
