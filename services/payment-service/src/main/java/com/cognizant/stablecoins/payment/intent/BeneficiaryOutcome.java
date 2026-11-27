package com.cognizant.stablecoins.payment.intent;

/** Crypto settlement and simulated fiat delivery are distinct outcomes. */
public enum BeneficiaryOutcome {
    USDC_WALLET, TOKEN_WALLET, SIMULATED_BANK_USD, SIMULATED_BANK_INR;

    public String payoutCurrency() {
        return switch (this) {
            case USDC_WALLET, TOKEN_WALLET -> null;
            case SIMULATED_BANK_USD -> "USD";
            case SIMULATED_BANK_INR -> "INR";
        };
    }
}
