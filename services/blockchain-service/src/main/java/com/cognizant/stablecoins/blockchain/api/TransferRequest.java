package com.cognizant.stablecoins.blockchain.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class TransferRequest {

    @NotBlank
    @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")
    private String paymentId;

    @NotBlank
    @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")
    private String correlationId;

    @NotBlank
    private String toAddress;

    @NotNull
    @DecimalMin("0.000001")
    private BigDecimal amount;

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
