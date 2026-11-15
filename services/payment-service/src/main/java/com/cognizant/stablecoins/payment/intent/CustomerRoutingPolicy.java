package com.cognizant.stablecoins.payment.intent;

import java.math.BigDecimal;
import java.util.Set;

/** Immutable server-configured policy snapshot; not a payment request field. */
public record CustomerRoutingPolicy(String version, Set<BeneficiaryOutcome> allowedOutcomes,
        Set<String> allowedProviderIds, BigDecimal maximumFeeUsd, int requiredApprovals) {
    /** Shared policy gate for confirmed structured instructions and unconfirmed agent drafts. */
    public void requireAllowed(Set<BeneficiaryOutcome> outcomes,BigDecimal feeUsd){
        if(!allowedOutcomes.containsAll(outcomes))throw new com.cognizant.stablecoins.payment.exception.PaymentValidationException("OUTCOME_NOT_ALLOWED","Requested outcomes are outside customer policy");
        if(feeUsd!=null&&feeUsd.compareTo(maximumFeeUsd)>0)throw new com.cognizant.stablecoins.payment.exception.PaymentValidationException("FEE_LIMIT_EXCEEDS_POLICY","Requested USD fee cap exceeds customer policy");
    }
    public CustomerRoutingPolicy {
        if (version == null || version.isBlank() || version.length() > 50
                || allowedOutcomes == null || allowedOutcomes.isEmpty()
                || allowedProviderIds == null || allowedProviderIds.isEmpty()
                || allowedProviderIds.stream().anyMatch(p -> p == null || p.isBlank())
                || maximumFeeUsd == null || maximumFeeUsd.signum() < 0
                || maximumFeeUsd.precision() - maximumFeeUsd.scale() > 16
                || maximumFeeUsd.stripTrailingZeros().scale() > 2 || requiredApprovals < 1) {
            throw new IllegalArgumentException("Invalid customer routing policy");
        }
        allowedOutcomes = Set.copyOf(allowedOutcomes);
        allowedProviderIds = Set.copyOf(allowedProviderIds);
    }
}
