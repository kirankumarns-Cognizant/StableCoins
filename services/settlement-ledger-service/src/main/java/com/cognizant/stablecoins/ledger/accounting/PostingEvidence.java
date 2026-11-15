package com.cognizant.stablecoins.ledger.accounting;

import java.math.BigDecimal;
import java.util.UUID;

/** Ledger-owned immutable snapshot of trusted quote/funding evidence, never model output. */
public record PostingEvidence(Mode mode, String policyVersion, Terms terms, String transactionHash, String payoutReference, PaymentAccountingService.ReceiptFee receiptFee) {
    public enum Mode { SIMULATED, REAL_TESTNET, MOCK }
    public enum Outcome { USDC_WALLET, SIMULATED_BANK_USD, SIMULATED_BANK_INR }
    public record Terms(UUID conversionId, UUID quoteId, int quoteVersion, String fundingReference,
            BigDecimal sourceUsd, BigDecimal transferUsdc, Outcome outcome, BigDecimal payoutAmount) {
        public Terms {
            if(sourceUsd!=null) sourceUsd=sourceUsd.stripTrailingZeros();
            if(transferUsdc!=null) transferUsdc=transferUsdc.stripTrailingZeros();
            if(payoutAmount!=null) payoutAmount=payoutAmount.stripTrailingZeros();
        }
    }
}
