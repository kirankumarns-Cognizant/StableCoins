package com.cognizant.stablecoins.payment.api.dto;

import java.math.BigDecimal;
import com.cognizant.stablecoins.payment.intent.PaymentIntentSnapshot;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PaymentResponse {

    private String paymentId;
    private String correlationId;
    private UUID senderAccountId;
    private UUID beneficiaryId;
    private BigDecimal amount;
    private String sourceCurrency;
    private String destinationCurrency;
    private String paymentRail;
    private String purpose;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private PaymentIntentSnapshot intent;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public PaymentIntentSnapshot getIntent() { return intent; }
    public void setIntent(PaymentIntentSnapshot intent) { this.intent = intent; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public UUID getSenderAccountId() { return senderAccountId; }
    public void setSenderAccountId(UUID senderAccountId) { this.senderAccountId = senderAccountId; }
    public UUID getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(UUID beneficiaryId) { this.beneficiaryId = beneficiaryId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getSourceCurrency() { return sourceCurrency; }
    public void setSourceCurrency(String sourceCurrency) { this.sourceCurrency = sourceCurrency; }
    public String getDestinationCurrency() { return destinationCurrency; }
    public void setDestinationCurrency(String destinationCurrency) { this.destinationCurrency = destinationCurrency; }
    public String getPaymentRail() { return paymentRail; }
    public void setPaymentRail(String paymentRail) { this.paymentRail = paymentRail; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
