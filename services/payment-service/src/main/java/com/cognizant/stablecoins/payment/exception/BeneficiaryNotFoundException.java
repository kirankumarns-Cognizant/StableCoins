package com.cognizant.stablecoins.payment.exception;

import java.util.UUID;

public class BeneficiaryNotFoundException extends PaymentException {

    public BeneficiaryNotFoundException(UUID beneficiaryId) {
        super("BENEFICIARY_NOT_FOUND", "Beneficiary not found: " + beneficiaryId);
    }
}
