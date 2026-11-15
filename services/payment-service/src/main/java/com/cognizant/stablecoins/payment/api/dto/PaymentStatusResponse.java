package com.cognizant.stablecoins.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public class PaymentStatusResponse {

    private final String paymentId;
    private final String correlationId;
    private final String status;
    private final OffsetDateTime updatedAt;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public PaymentStatusResponse(@JsonProperty("paymentId") String paymentId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("status") String status,
            @JsonProperty("updatedAt") OffsetDateTime updatedAt) {
        this.paymentId = paymentId;
        this.correlationId = correlationId;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getPaymentId() { return paymentId; }
    public String getCorrelationId() { return correlationId; }
    public String getStatus() { return status; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
