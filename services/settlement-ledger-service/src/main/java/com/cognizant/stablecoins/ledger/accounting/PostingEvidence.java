package com.cognizant.stablecoins.ledger.accounting;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.cognizant.stablecoins.ledger.besu.BesuAccountingEvidence;
import java.util.UUID;

/** Ledger-owned immutable snapshot of trusted quote/funding evidence, never model output. */
public record PostingEvidence(Mode mode, String policyVersion, Terms terms, String transactionHash, String payoutReference, PaymentAccountingService.ReceiptFee receiptFee,
        @JsonInclude(JsonInclude.Include.NON_NULL) BesuAccountingEvidence privateSettlement) {
    // Preserve legacy source callers and exact serialized evidence/fingerprints.
    public PostingEvidence(Mode mode, String policyVersion, Terms terms, String transactionHash,
            String payoutReference, PaymentAccountingService.ReceiptFee receiptFee) {
        this(mode, policyVersion, terms, transactionHash, payoutReference, receiptFee, null);
    }
    public enum Mode { SIMULATED, REAL_TESTNET, MOCK, PRIVATE_DEMO }
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
