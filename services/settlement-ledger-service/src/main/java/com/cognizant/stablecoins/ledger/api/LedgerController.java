package com.cognizant.stablecoins.ledger.api;

import com.cognizant.stablecoins.ledger.api.dto.*;
import com.cognizant.stablecoins.ledger.model.*;
import com.cognizant.stablecoins.ledger.repository.LedgerAccountRepository;
import com.cognizant.stablecoins.ledger.service.LedgerPostingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {

    private final com.cognizant.stablecoins.platform.security.PaymentOwnership ownership;
    private final LedgerPostingService postingService;
    private final boolean manualWrites;
    private final LedgerAccountRepository ledgerAccountRepository;

    public LedgerController(LedgerPostingService postingService,
                             LedgerAccountRepository ledgerAccountRepository,
                             @org.springframework.beans.factory.annotation.Value("${app.ledger.manual-api-enabled:false}") boolean manualWrites,
                             com.cognizant.stablecoins.platform.security.PaymentOwnership ownership) {
        this.ownership = ownership;
        this.postingService = postingService;
        this.manualWrites = manualWrites;
        this.ledgerAccountRepository = ledgerAccountRepository;
    }

    /**
     * Post a new balanced journal.
     * Idempotent: same (paymentId + journalType) returns existing POSTED journal.
     */
    @PostMapping("/journals")
    public ResponseEntity<JournalResponse> postJournal(@RequestBody PostJournalRequest request) {
        requireManualWrites();
        Journal journal = postingService.postJournal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toJournalResponse(journal));
    }

    /**
     * Get a specific journal with entries.
     */
    @GetMapping("/journals/{journalId}")
    public ResponseEntity<JournalResponse> getJournal(@PathVariable UUID journalId, org.springframework.security.core.Authentication authentication) {
        Journal journal = postingService.getJournal(journalId);
        ownership.require(journal.getPaymentId(), authentication);
        return ResponseEntity.ok(toJournalResponse(journal));
    }

    /**
     * Reverse a posted journal. Creates a compensating journal with swapped DR/CR.
     */
    @PostMapping("/journals/{journalId}/reverse")
    public ResponseEntity<JournalResponse> reverseJournal(@PathVariable UUID journalId) {
        requireManualWrites();
        Journal reversal = postingService.reverseJournal(journalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toJournalResponse(reversal));
    }

    /**
     * Get all journals for a payment, with entries.
     */
    @GetMapping("/payments/{paymentId}/ledger")
    public ResponseEntity<PaymentLedgerResponse> getPaymentLedger(@PathVariable String paymentId, org.springframework.security.core.Authentication authentication) {
        ownership.require(paymentId, authentication);
        List<Journal> journals = postingService.getJournalsByPaymentId(paymentId);
        List<JournalResponse> journalResponses = journals.stream()
                .map(this::toJournalResponse)
                .collect(Collectors.toList());
        PaymentLedgerResponse response = new PaymentLedgerResponse();
        response.setPaymentId(paymentId);
        response.setJournals(journalResponses);
        return ResponseEntity.ok(response);
    }

    private void requireManualWrites() {
        if (!manualWrites) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Manual ledger writes are disabled");
    }

    private JournalResponse toJournalResponse(Journal journal) {
        List<LedgerEntry> entries = postingService.getEntriesForJournal(journal.getJournalId());
        List<LedgerEntryResponse> entryResponses = entries.stream()
                .map(e -> {
                    LedgerEntryResponse r = new LedgerEntryResponse();
                    r.setEntryId(e.getEntryId());
                    r.setAsset(e.getAsset());
                    r.setDebit(e.getDebit());
                    r.setCredit(e.getCredit());
                    r.setReversalOf(e.getReversalOf());
                    // Enrich with account code/name
                    ledgerAccountRepository.findById(e.getLedgerAccountId()).ifPresent(acct -> {
                        r.setAccountCode(acct.getAccountCode());
                        r.setAccountName(acct.getAccountName());
                    });
                    return r;
                })
                .collect(Collectors.toList());

        JournalResponse r = new JournalResponse();
        r.setJournalId(journal.getJournalId());
        r.setPaymentId(journal.getPaymentId());
        r.setCorrelationId(journal.getCorrelationId());
        r.setJournalType(journal.getJournalType());
        r.setReference(journal.getReference());
        r.setDescription(journal.getDescription());
        r.setEvidence(journal.getEvidence());
        r.setStatus(journal.getStatus());
        r.setCreatedAt(journal.getCreatedAt());
        r.setPostedAt(journal.getPostedAt());
        r.setReversedByJournalId(journal.getReversedByJournalId());
        r.setEntries(entryResponses);
        return r;
    }
}
