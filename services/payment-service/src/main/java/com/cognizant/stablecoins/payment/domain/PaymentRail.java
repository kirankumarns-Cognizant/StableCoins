package com.cognizant.stablecoins.payment.domain;

/**
 * Supported payment rails for the POC.
 * Rail is selected in Iteration 3; created here as REQUESTED value.
 */
public enum PaymentRail {
    AUTO,
    STABLECOIN,
    VISA,
    STABLECOIN_PLUS_VISA
}
