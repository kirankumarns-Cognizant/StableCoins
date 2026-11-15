package com.cognizant.stablecoins.payment.exception;

import java.util.UUID;

public class AccountNotFoundException extends PaymentException {

    public AccountNotFoundException(UUID accountId) {
        super("ACCOUNT_NOT_FOUND", "Sender account not found: " + accountId);
    }
}
