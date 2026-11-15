package com.cognizant.stablecoins.payment.exception;

public class PaymentAccessException extends PaymentException {
    public PaymentAccessException(String code, String message) { super(code, message); }
}
