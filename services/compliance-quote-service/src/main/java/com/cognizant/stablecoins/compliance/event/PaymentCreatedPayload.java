package com.cognizant.stablecoins.compliance.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for the PaymentCreated domain event consumed by compliance-quote-service.
 */
public class PaymentCreatedPayload {

    private UUID senderAccountId;
    private UUID beneficiaryId;
    private BigDecimal amount;
    private String sourceCurrency;
    private String destinationCurrency;
    private String purpose;
    private String requestedRail;

    public PaymentCreatedPayload() {}

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
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getRequestedRail() { return requestedRail; }
    public void setRequestedRail(String requestedRail) { this.requestedRail = requestedRail; }
}
