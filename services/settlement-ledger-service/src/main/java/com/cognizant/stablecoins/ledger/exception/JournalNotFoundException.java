package com.cognizant.stablecoins.ledger.exception;
import java.util.UUID;
public class JournalNotFoundException extends RuntimeException {
    public JournalNotFoundException(UUID journalId) {
        super("Journal not found: " + journalId);
    }
}
