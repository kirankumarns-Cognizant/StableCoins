package com.cognizant.stablecoins.payment.api.dto;

import com.cognizant.stablecoins.payment.domain.PaymentPurpose;
import com.cognizant.stablecoins.payment.domain.PaymentRail;
import com.cognizant.stablecoins.payment.domain.SupportedCurrency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public class CreatePaymentRequest {

    @NotNull(message = "senderAccountId is required")
    private UUID senderAccountId;

    @NotNull(message = "beneficiaryId is required")
    private UUID beneficiaryId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @Digits(integer = 16, fraction = 2, message = "amount must have at most 16 integer digits and 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "currency is required")
    private SupportedCurrency currency;

    @NotNull(message = "purpose is required")
    private PaymentPurpose purpose;

    @NotNull(message = "requestedRail is required")
    private PaymentRail requestedRail;

    @Valid
    private PaymentIntentRequest intent;

    public PaymentIntentRequest getIntent() { return intent; }
    public void setIntent(PaymentIntentRequest intent) { this.intent = intent; }

    public UUID getSenderAccountId() { return senderAccountId; }
    public void setSenderAccountId(UUID senderAccountId) { this.senderAccountId = senderAccountId; }
    public UUID getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(UUID beneficiaryId) { this.beneficiaryId = beneficiaryId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public SupportedCurrency getCurrency() { return currency; }
    public void setCurrency(SupportedCurrency currency) { this.currency = currency; }
    public PaymentPurpose getPurpose() { return purpose; }
    public void setPurpose(PaymentPurpose purpose) { this.purpose = purpose; }
    public PaymentRail getRequestedRail() { return requestedRail; }
    public void setRequestedRail(PaymentRail requestedRail) { this.requestedRail = requestedRail; }
}
