package com.cognizant.stablecoins.payment.api.dto;

public class CreatePaymentResponse {

    private final String paymentId;
    private final String correlationId;
    private final String status;

    public CreatePaymentResponse(String paymentId, String correlationId, String status) {
        this.paymentId = paymentId;
        this.correlationId = correlationId;
        this.status = status;
    }

    public String getPaymentId() { return paymentId; }
    public String getCorrelationId() { return correlationId; }
    public String getStatus() { return status; }
}
