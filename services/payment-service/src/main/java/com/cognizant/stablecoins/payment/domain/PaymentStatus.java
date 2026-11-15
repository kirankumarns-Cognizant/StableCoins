package com.cognizant.stablecoins.payment.domain;

/**
 * Payment state model — from AGENTS.md section 5.
 * Iteration 2 creates payments in CREATED state only.
 * Subsequent states are set by later iterations.
 */
public enum PaymentStatus {
    CREATED,
    COMPLIANCE_PENDING,
    COMPLIANCE_APPROVED,
    COMPLIANCE_REJECTED,
    QUOTE_PENDING,
    QUOTE_ACCEPTED,
    RAIL_SELECTED,
    FUNDING_PENDING,
    FUNDED,
    BLOCKCHAIN_SUBMITTED,
    BLOCKCHAIN_CONFIRMED,
    SETTLEMENT_COMPLETED,
    VISA_PAYOUT_PENDING,
    VISA_PAYOUT_COMPLETED,
    RECONCILIATION_PENDING,
    RECONCILED,
    FAILED
}
