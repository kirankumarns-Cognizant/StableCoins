package com.cognizant.stablecoins.ledger.accounting;

import com.cognizant.stablecoins.ledger.api.dto.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LedgerIntegrityTest {
    AccountingFixture f;
    @BeforeEach void setup(){f=new AccountingFixture();}
    PostJournalRequest request(String amount){var r=new PostJournalRequest();r.setPaymentId("PAY-INTEGRITY");r.setCorrelationId("CORR-INTEGRITY");r.setJournalType("FUNDING");r.setReference("SIM-FUNDING");r.setEntries(List.of(f.policy.line(AccountingProperties.Role.FUNDING_USD,new BigDecimal(amount),true),f.policy.line(AccountingProperties.Role.CORPORATE_USD,new BigDecimal(amount),false)));return r;}
    @Test void changedAmountOnSameIdempotencyScopeIsRejected(){f.ledger.postJournal(request("10000"));assertThatThrownBy(()->f.ledger.postJournal(request("9999"))).hasMessage("Journal validation failed: JOURNAL_IDEMPOTENCY_CONFLICT");assertThat(f.lines).hasSize(2);}
    @Test void decimalScaleAndEntryOrderDoNotChangeBusinessIdentity(){var r=request("10000.00");var original=f.ledger.postJournal(r);var replay=request("10000");replay.setEntries(replay.getEntries().reversed());assertThat(f.ledger.postJournal(replay).getJournalId()).isEqualTo(original.getJournalId());}
    @Test void correlationCannotBeChangedForSamePayment(){f.ledger.postJournal(request("10000"));var r=request("10000");r.setCorrelationId("CORR-OTHER");assertThatThrownBy(()->f.ledger.postJournal(r)).hasMessage("Journal validation failed: LEDGER_CORRELATION_CONFLICT");}
    @Test void evidenceReferenceChangeIsNotDuplicateSuccess(){f.ledger.postJournal(request("10000"));var r=request("10000");r.setReference("OTHER-REFERENCE");assertThatThrownBy(()->f.ledger.postJournal(r)).hasMessage("Journal validation failed: JOURNAL_IDEMPOTENCY_CONFLICT");}
    @Test void accountAssetCannotBeSpoofedEvenWhenTheJournalBalances(){var r=request("10000");for(var line:r.getEntries())line.setAsset("USDC");assertThatThrownBy(()->f.ledger.postJournal(r)).hasMessage("Journal validation failed: LEDGER_ACCOUNT_ASSET_MISMATCH");assertThat(f.lines).isEmpty();}
    @Test void zeroLinesCannotBeUsedToCreateEmptyFinancialProof(){assertThatThrownBy(()->f.ledger.postJournal(request("0"))).hasMessage("Journal validation failed: entry must have one positive side");}
    @Test void excessPrecisionIsRejectedInsteadOfRounded(){assertThatThrownBy(()->f.ledger.postJournal(request("0.0000000000000000001"))).hasMessage("Journal validation failed: AMOUNT_PRECISION_EXCEEDED");}
    @Test void reversedPostingIsNotRecreatedByRedelivery(){var r=request("10000");var original=f.ledger.postJournal(r);f.ledger.reverseJournal(original.getJournalId());assertThatThrownBy(()->f.ledger.postJournal(r)).hasMessage("Journal validation failed: JOURNAL_NOT_REPLAYABLE");assertThat(f.lines).hasSize(4);}
    @Test void legacyReplayIsComparedAgainstOriginalEntriesWithoutMutatingThem(){var r=request("10000");var original=f.ledger.postJournal(r);original.setPostingFingerprint(null);assertThat(f.ledger.postJournal(request("10000.00")).getJournalId()).isEqualTo(original.getJournalId());assertThat(original.getPostingFingerprint()).isNull();assertThat(f.lines).hasSize(2);}
    @Test void changedLegacyAmountsFailClosed(){var original=f.ledger.postJournal(request("10000"));original.setPostingFingerprint(null);assertThatThrownBy(()->f.ledger.postJournal(request("9999"))).hasMessage("Journal validation failed: JOURNAL_IDEMPOTENCY_CONFLICT");}
}
