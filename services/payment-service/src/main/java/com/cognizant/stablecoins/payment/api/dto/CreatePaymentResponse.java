package com.cognizant.stablecoins.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CreatePaymentResponse {

    private final String paymentId;
    private final String correlationId;
    private final String status;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public CreatePaymentResponse(@JsonProperty("paymentId") String paymentId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("status") String status) {
        this.paymentId = paymentId;
        this.correlationId = correlationId;
        this.status = status;
    }

    public String getPaymentId() { return paymentId; }
    public String getCorrelationId() { return correlationId; }
    public String getStatus() { return status; }
}
