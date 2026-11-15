package com.cognizant.stablecoins.payment.api.dto;

import com.cognizant.stablecoins.payment.domain.SupportedCurrency;
import com.cognizant.stablecoins.payment.intent.BeneficiaryOutcome;
import com.cognizant.stablecoins.payment.intent.RoutingPriority;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** Confirmed structured intent. Policy, actor and settlement asset are server-owned. */
public record PaymentIntentRequest(
        @NotNull BeneficiaryOutcome requiredOutcome,
        @NotNull Instant deliverBy,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal maximumFee,
        @NotNull SupportedCurrency feeCurrency,
        @NotNull RoutingPriority priority,
        @NotNull Set<@NotNull BeneficiaryOutcome> allowedAlternatives,
        @NotNull @AssertTrue Boolean confirmed) {
    public PaymentIntentRequest {
        if (allowedAlternatives != null) {
            allowedAlternatives = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(allowedAlternatives));
        }
    }
}
