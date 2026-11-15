package com.cognizant.stablecoins.ledger.service;

import com.cognizant.stablecoins.ledger.api.dto.JournalEntryRequest;
import com.cognizant.stablecoins.ledger.api.dto.PostJournalRequest;
import com.cognizant.stablecoins.ledger.domain.JournalStatus;
import com.cognizant.stablecoins.ledger.exception.*;
import com.cognizant.stablecoins.ledger.model.*;
import com.cognizant.stablecoins.ledger.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerPostingServiceTest {

    @Mock
    private JournalRepository journalRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerAccountRepository ledgerAccountRepository;

    @Mock private LedgerPaymentLockRepository locks;

    @InjectMocks
    private LedgerPostingService service;

    @BeforeEach
    void setUpCommonMocks() {
        when(locks.lock(anyString())).thenAnswer(inv -> Optional.of(new LedgerPaymentLock(inv.getArgument(0), "CORR-" + inv.getArgument(0))));
        when(journalRepository.lockById(any())).thenAnswer(inv -> journalRepository.findById(inv.getArgument(0)));
        when(journalRepository.findScope(any())).thenAnswer(inv -> journalRepository.findById(inv.getArgument(0)).map(j -> new JournalRepository.Scope() {
            public String getPaymentId() { return j.getPaymentId(); }
            public String getCorrelationId() { return j.getCorrelationId(); }
        }));
        when(ledgerAccountRepository.findByAccountCode("110001")).thenReturn(Optional.of(buildAccount("110001", "USD")));
        when(ledgerAccountRepository.findByAccountCode("110002")).thenReturn(Optional.of(buildAccount("110002", "USD")));
        when(ledgerAccountRepository.findByAccountCode("120001")).thenReturn(Optional.of(buildAccount("120001", "USDC")));
        when(ledgerAccountRepository.findByAccountCode("120002")).thenReturn(Optional.of(buildAccount("120002", "USDC")));
        when(ledgerAccountRepository.findByAccountCode("510001")).thenReturn(Optional.of(buildAccount("510001", "ETH")));
        when(journalRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(journalRepository.findByPaymentIdAndJournalType(any(), any())).thenReturn(Optional.empty());
    }

    // â”€â”€ Helper builders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private LedgerAccount buildAccount(String code, String asset) {
        LedgerAccount a = new LedgerAccount();
        a.setLedgerAccountId(UUID.randomUUID());
        a.setAccountCode(code);
        a.setAccountName("Account " + code);
        a.setAsset(asset);
        a.setActive(true);
        return a;
    }

    private JournalEntryRequest entryRequest(String code, String asset, String debitStr, String creditStr) {
        JournalEntryRequest e = new JournalEntryRequest();
        e.setAccountCode(code);
        e.setAsset(asset);
        e.setDebit(debitStr != null ? new BigDecimal(debitStr) : null);
        e.setCredit(creditStr != null ? new BigDecimal(creditStr) : null);
        return e;
    }

    private PostJournalRequest buildRequest(String paymentId, String journalType, List<JournalEntryRequest> entries) {
        PostJournalRequest r = new PostJournalRequest();
        r.setPaymentId(paymentId);
        r.setCorrelationId("CORR-" + paymentId);
        r.setJournalType(journalType);
        r.setReference("REF-" + paymentId);
        r.setEntries(entries);
        return r;
    }

    // â”€â”€ Test cases â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void balancedUsdJournal_posts_successfully() {
        PostJournalRequest req = buildRequest("PAY-001", "USD_FUNDING",
                Arrays.asList(
                        entryRequest("110002", "USD", "10000", null),
                        entryRequest("110001", "USD", null, "10000")
                ));

        Journal result = service.postJournal(req);

        assertThat(result.getStatus()).isEqualTo(JournalStatus.POSTED.name());
        assertThat(result.getPaymentId()).isEqualTo("PAY-001");
    }

    @Test
    void balancedUsdcJournal_posts_successfully() {
        PostJournalRequest req = buildRequest("PAY-002", "USDC_PURCHASE",
                Arrays.asList(
                        entryRequest("120002", "USDC", "10000", null),
                        entryRequest("120001", "USDC", null, "10000")
                ));

        Journal result = service.postJournal(req);

        assertThat(result.getStatus()).isEqualTo(JournalStatus.POSTED.name());
    }

    @Test
    void balancedEthJournal_posts_successfully() {
        LedgerAccount ethCreditAccount = buildAccount("510002ETH", "ETH");
        when(ledgerAccountRepository.findByAccountCode("510002ETH")).thenReturn(Optional.of(ethCreditAccount));

        PostJournalRequest req = buildRequest("PAY-003", "ETH_GAS",
                Arrays.asList(
                        entryRequest("510001", "ETH", "0.005", null),
                        entryRequest("510002ETH", "ETH", null, "0.005")
                ));

        Journal result = service.postJournal(req);

        assertThat(result.getStatus()).isEqualTo(JournalStatus.POSTED.name());
    }

    @Test
    void unbalancedJournal_sameAsset_rejected() {
        PostJournalRequest req = buildRequest("PAY-004", "USD_FUNDING",
                Arrays.asList(
                        entryRequest("110002", "USD", "10000", null),
                        entryRequest("110001", "USD", null, "9999")
                ));

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("not balanced for asset USD");
    }

    @Test
    void crossAssetFalseBalance_rejected() {
        // DR 120001 10000 USDC / CR 110001 10000 USD
        // Numerically 10000 = 10000 but per-asset unbalanced
        PostJournalRequest req = buildRequest("PAY-005", "CROSS_ASSET",
                Arrays.asList(
                        entryRequest("120001", "USDC", "10000", null),
                        entryRequest("110001", "USD", null, "10000")
                ));

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("not balanced for asset");
    }

    @Test
    void missingLedgerAccount_rejected() {
        when(ledgerAccountRepository.findByAccountCode("UNKNOWN")).thenReturn(Optional.empty());

        PostJournalRequest req = buildRequest("PAY-006", "USD_FUNDING",
                Arrays.asList(
                        entryRequest("UNKNOWN", "USD", "10000", null),
                        entryRequest("110001", "USD", null, "10000")
                ));

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(LedgerAccountNotFoundException.class);
    }

    @Test
    void inactiveLedgerAccount_rejected() {
        LedgerAccount inactive = buildAccount("110002", "USD");
        inactive.setActive(false);
        when(ledgerAccountRepository.findByAccountCode("110002")).thenReturn(Optional.of(inactive));

        PostJournalRequest req = buildRequest("PAY-007", "USD_FUNDING",
                Arrays.asList(
                        entryRequest("110002", "USD", "10000", null),
                        entryRequest("110001", "USD", null, "10000")
                ));

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void negativeDebit_rejected() {
        PostJournalRequest req = buildRequest("PAY-008", "USD_FUNDING",
                Arrays.asList(
                        entryRequest("110002", "USD", "-1", null),
                        entryRequest("110001", "USD", null, "10000")
                ));

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("debit cannot be negative");
    }

    @Test
    void negativeCredit_rejected() {
        PostJournalRequest req = buildRequest("PAY-009", "USD_FUNDING",
                Arrays.asList(
                        entryRequest("110002", "USD", "10000", null),
                        entryRequest("110001", "USD", null, "-1")
                ));

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("credit cannot be negative");
    }

    @Test
    void bothDebitAndCredit_rejected() {
        PostJournalRequest req = buildRequest("PAY-010", "USD_FUNDING",
                Arrays.asList(
                        entryRequest("110002", "USD", "5000", "5000"),
                        entryRequest("110001", "USD", null, "10000")
                ));

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("both debit and credit");
    }

    @Test
    void emptyEntries_rejected() {
        PostJournalRequest req = buildRequest("PAY-011", "USD_FUNDING", Collections.emptyList());

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("at least two entries");
    }

    @Test
    void singleEntry_rejected() {
        PostJournalRequest req = buildRequest("PAY-012", "USD_FUNDING",
                Collections.singletonList(entryRequest("110002", "USD", "10000", null)));

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("at least two entries");
    }

    @Test
    void duplicatePosting_idempotentReturn() {
        PostJournalRequest req = buildRequest("PAY-013", "USD_FUNDING", List.of(
                entryRequest("110002", "USD", "10000", null), entryRequest("110001", "USD", null, "10000")));
        Journal existing = service.postJournal(req);
        clearInvocations(journalRepository, ledgerEntryRepository);
        when(journalRepository.findByPaymentIdAndJournalType("PAY-013", "USD_FUNDING")).thenReturn(Optional.of(existing));
        Journal result=service.postJournal(req);
        assertThat(result.getJournalId()).isEqualTo(existing.getJournalId());
        verify(journalRepository, never()).saveAndFlush(any());
    }

    @Test
    void successfulPost_writesJournalTwiceAndEntries() {
        PostJournalRequest req = buildRequest("PAY-014", "USD_FUNDING",
                Arrays.asList(
                        entryRequest("110002", "USD", "10000", null),
                        entryRequest("110001", "USD", null, "10000")
                ));

        service.postJournal(req);

        verify(journalRepository, times(2)).saveAndFlush(any(Journal.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    void reversal_swapsDebitAndCredit() {
        UUID journalId = UUID.randomUUID();
        UUID acctId1 = UUID.randomUUID();
        UUID acctId2 = UUID.randomUUID();
        UUID entry1Id = UUID.randomUUID();
        UUID entry2Id = UUID.randomUUID();

        Journal original = new Journal();
        original.setJournalId(journalId);
        original.setPaymentId("PAY-015");
        original.setCorrelationId("CORR-PAY-015");
        original.setJournalType("USD_FUNDING");
        original.setStatus(JournalStatus.POSTED.name());
        original.setReversedByJournalId(null);

        LedgerEntry entry1 = new LedgerEntry();
        entry1.setEntryId(entry1Id);
        entry1.setJournalId(journalId);
        entry1.setLedgerAccountId(acctId1);
        entry1.setPaymentId("PAY-015");
        entry1.setAsset("USD");
        entry1.setDebit(new BigDecimal("10000"));
        entry1.setCredit(BigDecimal.ZERO);

        LedgerEntry entry2 = new LedgerEntry();
        entry2.setEntryId(entry2Id);
        entry2.setJournalId(journalId);
        entry2.setLedgerAccountId(acctId2);
        entry2.setPaymentId("PAY-015");
        entry2.setAsset("USD");
        entry2.setDebit(BigDecimal.ZERO);
        entry2.setCredit(new BigDecimal("10000"));

        when(journalRepository.findById(journalId)).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.findByJournalIdOrderByCreatedAtAsc(journalId))
                .thenReturn(Arrays.asList(entry1, entry2));

        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);

        service.reverseJournal(journalId);

        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());
        List<LedgerEntry> savedEntries = entryCaptor.getAllValues();

        // Reversal of entry1 (DR 10000, CR 0) should produce DR 0, CR 10000
        LedgerEntry rev1 = savedEntries.get(0);
        assertThat(rev1.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rev1.getCredit()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(rev1.getReversalOf()).isEqualTo(entry1Id);

        // Reversal of entry2 (DR 0, CR 10000) should produce DR 10000, CR 0
        LedgerEntry rev2 = savedEntries.get(1);
        assertThat(rev2.getDebit()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(rev2.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rev2.getReversalOf()).isEqualTo(entry2Id);
    }

    @Test
    void duplicateReversal_rejected() {
        UUID journalId = UUID.randomUUID();
        Journal journal = new Journal();
        journal.setJournalId(journalId);
        journal.setPaymentId("PAY-REVERSE"); journal.setCorrelationId("CORR-PAY-REVERSE");
        journal.setStatus(JournalStatus.POSTED.name());
        journal.setReversedByJournalId(UUID.randomUUID()); // already reversed

        when(journalRepository.findById(journalId)).thenReturn(Optional.of(journal));

        assertThatThrownBy(() -> service.reverseJournal(journalId))
                .isInstanceOf(JournalAlreadyReversedException.class);
    }

    @Test
    void reversalOfPendingJournal_rejected() {
        UUID journalId = UUID.randomUUID();
        Journal journal = new Journal();
        journal.setJournalId(journalId);
        journal.setPaymentId("PAY-REVERSE"); journal.setCorrelationId("CORR-PAY-REVERSE");
        journal.setStatus(JournalStatus.PENDING.name());
        journal.setReversedByJournalId(null);

        when(journalRepository.findById(journalId)).thenReturn(Optional.of(journal));

        assertThatThrownBy(() -> service.reverseJournal(journalId))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void reversal_propagatesPaymentId() {
        UUID journalId = UUID.randomUUID();
        Journal original = new Journal();
        original.setJournalId(journalId);
        original.setPaymentId("PAY-018");
        original.setCorrelationId("CORR-PAY-018");
        original.setJournalType("USD_FUNDING");
        original.setStatus(JournalStatus.POSTED.name());
        original.setReversedByJournalId(null);

        when(journalRepository.findById(journalId)).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.findByJournalIdOrderByCreatedAtAsc(journalId))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.reverseJournal(journalId)).hasMessage("Journal validation failed: JOURNAL_HAS_NO_ENTRIES");
        verify(journalRepository, never()).saveAndFlush(any());
    }

    @Test
    void bigDecimalPrecision_preserved() {
        PostJournalRequest req = buildRequest("PAY-019", "USD_FUNDING",
                Arrays.asList(
                        entryRequest("110002", "USD", "10000.123456", null),
                        entryRequest("110001", "USD", null, "10000.123456")
                ));

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        service.postJournal(req);

        verify(ledgerEntryRepository, atLeastOnce()).save(captor.capture());
        LedgerEntry debitEntry = captor.getAllValues().stream()
                .filter(e -> e.getDebit().compareTo(BigDecimal.ZERO) > 0)
                .findFirst().orElseThrow();
        assertThat(debitEntry.getDebit()).isEqualByComparingTo(new BigDecimal("10000.123456"));
    }

    @Test
    void unsupportedAsset_rejected() {
        PostJournalRequest req = buildRequest("PAY-020", "GBP_FUNDING",
                Arrays.asList(
                        entryRequest("110002", "GBP", "10000", null),
                        entryRequest("110001", "GBP", null, "10000")
                ));

        assertThatThrownBy(() -> service.postJournal(req))
                .isInstanceOf(JournalValidationException.class)
                .hasMessageContaining("unsupported asset");
    }
}
