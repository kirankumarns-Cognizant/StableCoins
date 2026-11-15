package com.cognizant.stablecoins.ledger.exception;
import java.util.UUID;
public class JournalAlreadyReversedException extends RuntimeException {
    public JournalAlreadyReversedException(UUID journalId) {
        super("Journal already reversed: " + journalId);
    }
}
