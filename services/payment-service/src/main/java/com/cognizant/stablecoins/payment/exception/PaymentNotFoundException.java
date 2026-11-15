package com.cognizant.stablecoins.payment.exception;

public class PaymentNotFoundException extends PaymentException {

    public PaymentNotFoundException(String paymentId) {
        super("PAYMENT_NOT_FOUND", "Payment not found: " + paymentId);
    }
}
