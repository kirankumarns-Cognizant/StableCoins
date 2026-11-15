package com.cognizant.stablecoins.payment.routing;

import com.cognizant.stablecoins.payment.domain.PaymentRail;
import com.cognizant.stablecoins.payment.intent.BeneficiaryOutcome;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Trusted server input: amounts must come from validated instruction/quotes and gas estimation.
 * A readiness assessment does not reserve funds or establish an execution authorization.
 */
public record AssessmentContext(String paymentId, String correlationId, UUID customerId, String corridor,
        BigDecimal sourceAmountUsd, BigDecimal transferAmountUsdc, BigDecimal requiredGasEth,
        Map<BeneficiaryOutcome, BigDecimal> requiredPayoutAmounts, PaymentRail requestedRail) {
    public AssessmentContext { requiredPayoutAmounts = Map.copyOf(requiredPayoutAmounts); }
}
