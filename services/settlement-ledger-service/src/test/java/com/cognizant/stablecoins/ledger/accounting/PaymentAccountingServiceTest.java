package com.cognizant.stablecoins.ledger.accounting;

import org.junit.jupiter.api.*;
import java.math.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.ledger.accounting.PostingEvidence.*;
import static com.cognizant.stablecoins.ledger.accounting.PaymentAccountingService.*;

class PaymentAccountingServiceTest {
    AccountingFixture f;
    @BeforeEach void setup(){f=new AccountingFixture();}
    @Test void conversionKeepsIndependentQuoteAmountsAndEveryAssetBalances() {
        var c=f.context(Outcome.USDC_WALLET);var result=f.service.fundAndConvert(c);
        assertThat(result).extracting(j->j.getJournalType()).containsExactly("FUNDING","CONVERSION_USD","CONVERSION_USDC");
        for(var journal:result) {
            var amounts=new HashMap<String,BigDecimal>();
            for(var line:f.lines) if(line.getJournalId().equals(journal.getJournalId())) amounts.merge(line.getAsset(),line.getDebit().subtract(line.getCredit()),BigDecimal::add);
            assertThat(amounts.values()).allMatch(a->a.signum()==0);
            assertThat(journal.getEvidence().mode()).isEqualTo(Mode.SIMULATED);
        }
        assertThat(f.lines).anyMatch(e->e.getAsset().equals("USDC") && e.getDebit().compareTo(new BigDecimal("9995.125"))==0);
    }
    @Test void matchingConversionReplayAddsNoEntries() {var c=f.context(Outcome.USDC_WALLET); f.service.fundAndConvert(c);f.service.fundAndConvert(c);assertThat(f.saved).hasSize(3);assertThat(f.lines).hasSize(6);}
    @Test void changedQuoteOnSamePaymentConflicts() {f.service.fundAndConvert(f.context(Outcome.USDC_WALLET));assertThatThrownBy(()->f.service.fundAndConvert(f.context(Outcome.USDC_WALLET))).hasMessage("Journal validation failed: JOURNAL_IDEMPOTENCY_CONFLICT");}
    @Test void partialConversionIsQuarantined() {var c=f.context(Outcome.USDC_WALLET);f.service.fundAndConvert(c);f.saved.values().removeIf(j->j.getJournalType().equals("CONVERSION_USDC"));assertThatThrownBy(()->f.service.fundAndConvert(c)).hasMessage("Journal validation failed: PARTIAL_COMPOUND_JOURNAL_REVIEW_REQUIRED");}
    @Test void confirmationDebitsBeneficiaryAndCreditsTransit() {
        var c=f.context(Outcome.USDC_WALLET);f.submitted(c);var journal=f.service.confirmed(c,f.hash,Mode.MOCK);
        var lines=f.lines.stream().filter(e->e.getJournalId().equals(journal.getJournalId())).toList();
        assertThat(lines.get(0).getLedgerAccountId()).isEqualTo(f.chart.get("120003").getLedgerAccountId());
        assertThat(lines.get(0).getDebit()).isEqualByComparingTo(c.terms().transferUsdc());
        assertThat(lines.get(1).getLedgerAccountId()).isEqualTo(f.chart.get("120002").getLedgerAccountId());
    }
    @Test void confirmationRequiresMatchingSubmission() {var c=f.context(Outcome.USDC_WALLET);f.service.fundAndConvert(c);assertThatThrownBy(()->f.service.confirmed(c,f.hash,Mode.MOCK)).hasMessage("Journal validation failed: PRIOR_ACCOUNTING_LEG_REQUIRED");}
    @Test void changedTransactionCannotCreateSecondFinancialLeg() {var c=f.context(Outcome.USDC_WALLET);f.submitted(c);assertThatThrownBy(()->f.service.confirmed(c,"0x"+"b".repeat(64),Mode.MOCK)).hasMessage("Journal validation failed: PRIOR_ACCOUNTING_LEG_MISMATCH");}
    @Test void chainModeCannotBeLabeledFiatSimulation() {var c=f.context(Outcome.USDC_WALLET);assertThatThrownBy(()->f.service.submitted(c,f.hash,Mode.SIMULATED)).hasMessage("Journal validation failed: CHAIN_EVIDENCE_MODE_REQUIRED");}
    @Test void actualEthFeePreservesWeiPrecisionAndIncludesAdditionalComponents() {
        var c=f.context(Outcome.USDC_WALLET);f.submitted(c);var r=fee(FeeSource.TRANSACTION_RECEIPT,true,BigInteger.ONE);
        var journal=f.service.networkFee(c,r,Mode.MOCK);
        assertThat(f.lines.stream().filter(e->e.getJournalId().equals(journal.getJournalId())).findFirst().orElseThrow().getDebit()).isEqualByComparingTo("0.000000000000063001");
    }
    @Test void gasEstimateIsNeverPostedAsActualFee() {var c=f.context(Outcome.USDC_WALLET);f.submitted(c);assertThatThrownBy(()->f.service.networkFee(c,fee(FeeSource.GAS_ESTIMATE,true,BigInteger.ONE),Mode.MOCK)).hasMessage("Journal validation failed: FEE_EVIDENCE_INCOMPLETE");}
    @Test void incompleteBaseFeeComponentsAreNotAccepted() {var c=f.context(Outcome.USDC_WALLET);f.submitted(c);assertThatThrownBy(()->f.service.networkFee(c,fee(FeeSource.TRANSACTION_RECEIPT,false,null),Mode.MOCK)).hasMessage("Journal validation failed: FEE_EVIDENCE_INCOMPLETE");}
    @Test void inconsistentReceiptFeeFailsClosed() {var c=f.context(Outcome.USDC_WALLET);f.submitted(c);var r=fee(FeeSource.TRANSACTION_RECEIPT,true,BigInteger.ONE);var bad=new ReceiptFee(r.transactionHash(),r.chainId(),r.blockNumber(),r.gasUsed(),r.effectiveGasPriceWei(),r.additionalFeeWei(),BigInteger.ONE,true,r.source());assertThatThrownBy(()->f.service.networkFee(c,bad,Mode.MOCK)).hasMessage("Journal validation failed: FEE_EVIDENCE_INCONSISTENT");}
    @Test void feeCanBeRecordedWithoutSuccessfulUsdcConfirmation() {var c=f.context(Outcome.USDC_WALLET);f.submitted(c);f.service.networkFee(c,fee(FeeSource.TRANSACTION_RECEIPT,true,BigInteger.ONE),Mode.MOCK);assertThat(f.saved.values()).noneMatch(j->j.getJournalType().equals("BLOCKCHAIN_CONFIRMATION"));}
    @Test void walletDeliveryRejectsOptionalFiatPayout() {var c=f.context(Outcome.USDC_WALLET);f.confirmed(c);assertThatThrownBy(()->f.service.initiatePayout(c,f.hash,Mode.MOCK,"VISA-SIM-1")).hasMessage("Journal validation failed: WALLET_OUTCOME_HAS_NO_FIAT_PAYOUT");}
    @Test void inrPayoutUsesOnlyInrAccountsAndSimulatedLabels() {
        var c=f.context(Outcome.SIMULATED_BANK_INR);f.confirmed(c);var init=f.service.initiatePayout(c,f.hash,Mode.MOCK,"VISA-SIM-1");
        var done=f.service.completePayout(c,f.hash,"VISA-SIM-1",PayoutStatus.SUCCEEDED);
        assertThat(init).hasSize(2);assertThat(done.getJournalType()).isEqualTo("PAYOUT_COMPLETION_INR");assertThat(done.getEvidence().mode()).isEqualTo(Mode.SIMULATED);
        assertThat(f.lines.stream().filter(e->e.getJournalId().equals(done.getJournalId()))).allMatch(e->e.getAsset().equals("INR"));
    }
    @Test void usdPayoutReplayDoesNotDuplicateEntries() {var c=f.context(Outcome.SIMULATED_BANK_USD);f.confirmed(c);f.service.initiatePayout(c,f.hash,Mode.MOCK,"VISA-SIM-1");f.service.completePayout(c,f.hash,"VISA-SIM-1",PayoutStatus.SUCCEEDED);int count=f.lines.size();f.service.initiatePayout(c,f.hash,Mode.MOCK,"VISA-SIM-1");f.service.completePayout(c,f.hash,"VISA-SIM-1",PayoutStatus.SUCCEEDED);assertThat(f.lines).hasSize(count);}
    @Test void failedOrUnknownPayoutCannotBecomePaidOrTriggerReversal() {var c=f.context(Outcome.SIMULATED_BANK_USD);f.confirmed(c);f.service.initiatePayout(c,f.hash,Mode.MOCK,"VISA-SIM-1");for(var status:List.of(PayoutStatus.FAILED,PayoutStatus.UNKNOWN))assertThatThrownBy(()->f.service.completePayout(c,f.hash,"VISA-SIM-1",status)).hasMessage("Journal validation failed: PAYOUT_NOT_CONFIRMED_SUCCESSFUL");assertThat(f.saved.values()).noneMatch(j->j.getJournalType().startsWith("REVERSAL"));}
    @Test void completionCannotFabricatePayoutInitiation() {var c=f.context(Outcome.SIMULATED_BANK_USD);f.confirmed(c);assertThatThrownBy(()->f.service.completePayout(c,f.hash,"VISA-SIM-1",PayoutStatus.SUCCEEDED)).hasMessage("Journal validation failed: PRIOR_ACCOUNTING_LEG_REQUIRED");}
    @Test void changedPayoutReferenceConflicts() {var c=f.context(Outcome.SIMULATED_BANK_USD);f.confirmed(c);f.service.initiatePayout(c,f.hash,Mode.MOCK,"VISA-SIM-1");assertThatThrownBy(()->f.service.completePayout(c,f.hash,"VISA-SIM-2",PayoutStatus.SUCCEEDED)).hasMessage("Journal validation failed: PRIOR_ACCOUNTING_LEG_MISMATCH");}
    @Test void invalidUsdcPrecisionIsRejectedBeforePosting() {var c=f.context(Outcome.USDC_WALLET);var t=c.terms();var bad=new Context(c.paymentId(),c.correlationId(),new Terms(t.conversionId(),t.quoteId(),1,t.fundingReference(),t.sourceUsd(),new BigDecimal("1.0000001"),t.outcome(),null));assertThatThrownBy(()->f.service.fundAndConvert(bad)).hasMessage("Journal validation failed: ACCOUNTING_AMOUNT_INVALID");assertThat(f.lines).isEmpty();}
    ReceiptFee fee(FeeSource source,boolean complete,BigInteger additional) {return new ReceiptFee(f.hash,84532,123,BigInteger.valueOf(21000),BigInteger.valueOf(3),additional,BigInteger.valueOf(63001),complete,source);}
}
