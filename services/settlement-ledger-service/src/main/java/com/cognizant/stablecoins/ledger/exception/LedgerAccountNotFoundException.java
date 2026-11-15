package com.cognizant.stablecoins.ledger.exception;
public class LedgerAccountNotFoundException extends RuntimeException {
    public LedgerAccountNotFoundException(String accountCode) {
        super("Ledger account not found: " + accountCode);
    }
}
