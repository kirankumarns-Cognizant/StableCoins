package com.cognizant.stablecoins.ledger.exception;

public class JournalValidationException extends RuntimeException {
    private final String reason;
    public JournalValidationException(String reason) {
        super("Journal validation failed: " + reason);
        this.reason = reason;
    }
    public String getReason() { return reason; }
}
