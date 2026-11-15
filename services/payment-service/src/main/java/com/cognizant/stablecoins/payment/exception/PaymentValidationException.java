package com.cognizant.stablecoins.payment.exception;

public class PaymentValidationException extends PaymentException {

    public PaymentValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
