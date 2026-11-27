package com.cognizant.stablecoins.ledger.service;

import com.cognizant.stablecoins.ledger.api.dto.*;
import com.cognizant.stablecoins.ledger.domain.*;
import com.cognizant.stablecoins.ledger.exception.*;
import com.cognizant.stablecoins.ledger.model.*;
import com.cognizant.stablecoins.ledger.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class LedgerPostingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LedgerPostingService.class);
    private final JournalRepository journals;
    private final LedgerEntryRepository entries;
    private final LedgerAccountRepository accounts;
    private final LedgerPaymentLockRepository locks;
    private static final ObjectMapper JSON = new ObjectMapper();

    public LedgerPostingService(JournalRepository journals, LedgerEntryRepository entries,
            LedgerAccountRepository accounts, LedgerPaymentLockRepository locks) {
        this.journals = journals; this.entries = entries; this.accounts = accounts; this.locks = locks;
    }

    /** Serialize every posting/reversal for a payment without writing another service's tables. */
    @Transactional
    public void lockPayment(String paymentId, String correlationId) {
        identifier(paymentId, "paymentId"); identifier(correlationId, "correlationId");
        locks.ensure(paymentId, correlationId);
        var lock = locks.lock(paymentId).orElseThrow(() -> invalid("LEDGER_LOCK_UNAVAILABLE"));
        if (!correlationId.equals(lock.getCorrelationId())) throw invalid("LEDGER_CORRELATION_CONFLICT");
    }

    @Transactional
    public Journal postJournal(PostJournalRequest request) {
        validate(request);
        lockPayment(request.getPaymentId(), request.getCorrelationId());
        Map<String, LedgerAccount> resolved = resolve(request.getEntries());
        balance(request.getEntries());
        String fingerprint = fingerprint(request, resolved);
        var prior = journals.findByPaymentIdAndJournalType(request.getPaymentId(), request.getJournalType());
        if (prior.isPresent()) {
            Journal old = prior.get();
            if (!JournalStatus.POSTED.name().equals(old.getStatus())) throw invalid("JOURNAL_NOT_REPLAYABLE");
            String previous = old.getPostingFingerprint();
            if (previous == null) previous = legacyFingerprint(old);
            if (!previous.equals(fingerprint)) throw invalid("JOURNAL_IDEMPOTENCY_CONFLICT");
            return old;
        }
        Journal journal = new Journal();
        journal.setJournalId(UUID.randomUUID()); journal.setPaymentId(request.getPaymentId());
        journal.setCorrelationId(request.getCorrelationId()); journal.setJournalType(request.getJournalType());
        journal.setReference(request.getReference()); journal.setDescription(request.getDescription());
        journal.setEvidence(request.getEvidence()); journal.setPostingFingerprint(fingerprint);
        journal.setStatus(JournalStatus.PENDING.name());
        // Flush PENDING before inserting lines; database forbids inserting lines into posted journals.
        journal = journals.saveAndFlush(journal);
        for (JournalEntryRequest line : request.getEntries()) {
            LedgerEntry entry = new LedgerEntry();
            entry.setEntryId(UUID.randomUUID()); entry.setJournalId(journal.getJournalId());
            entry.setLedgerAccountId(resolved.get(line.getAccountCode()).getLedgerAccountId());
            entry.setPaymentId(request.getPaymentId()); entry.setAsset(line.getAsset());
            entry.setDebit(zero(line.getDebit())); entry.setCredit(zero(line.getCredit()));
            entries.save(entry);
        }
        entries.flush();
        journal.setStatus(JournalStatus.POSTED.name()); journal.setPostedAt(OffsetDateTime.now());
        Journal posted = journals.saveAndFlush(journal);
        log.info("service=settlement-ledger-service paymentId={} correlationId={} journalId={} status=POSTED", posted.getPaymentId(), posted.getCorrelationId(), posted.getJournalId());
        return posted;
    }

    @Transactional
    public Journal reverseJournal(UUID journalId) {
        var scope = journals.findScope(journalId).orElseThrow(() -> new JournalNotFoundException(journalId));
        lockPayment(scope.getPaymentId(), scope.getCorrelationId());
        Journal original = journals.lockById(journalId).orElseThrow(() -> new JournalNotFoundException(journalId));
        if (original.getReversedByJournalId() != null) throw new JournalAlreadyReversedException(journalId);
        if (!JournalStatus.POSTED.name().equals(original.getStatus()))
            throw invalid("Only POSTED journals can be reversed, current status: " + original.getStatus());
        var originals = entries.findByJournalIdOrderByCreatedAtAsc(journalId);
        if (originals.isEmpty()) throw invalid("JOURNAL_HAS_NO_ENTRIES");
        Journal reversal = new Journal();
        reversal.setJournalId(UUID.randomUUID()); reversal.setPaymentId(original.getPaymentId());
        reversal.setCorrelationId(original.getCorrelationId()); reversal.setJournalType("REVERSAL_OF_" + original.getJournalType());
        reversal.setReference("REVERSAL:" + journalId); reversal.setDescription("Reversal of journal " + journalId);
        reversal.setEvidence(original.getEvidence()); reversal.setStatus(JournalStatus.PENDING.name());
        reversal = journals.saveAndFlush(reversal);
        for (LedgerEntry old : originals) {
            LedgerEntry line = new LedgerEntry();
            line.setEntryId(UUID.randomUUID()); line.setJournalId(reversal.getJournalId());
            line.setLedgerAccountId(old.getLedgerAccountId()); line.setPaymentId(original.getPaymentId());
            line.setAsset(old.getAsset()); line.setDebit(old.getCredit()); line.setCredit(old.getDebit());
            line.setReversalOf(old.getEntryId()); entries.save(line);
        }
        entries.flush();
        reversal.setStatus(JournalStatus.POSTED.name()); reversal.setPostedAt(OffsetDateTime.now());
        journals.saveAndFlush(reversal);
        original.setStatus(JournalStatus.REVERSED.name()); original.setReversedByJournalId(reversal.getJournalId());
        journals.saveAndFlush(original);
        return reversal;
    }

    @Transactional(readOnly = true)
    public Journal getJournal(UUID id) { return journals.findById(id).orElseThrow(() -> new JournalNotFoundException(id)); }
    @Transactional(readOnly = true)
    public List<Journal> getJournalsByPaymentId(String id) { return journals.findByPaymentIdOrderByCreatedAtAsc(id); }
    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesForJournal(UUID id) { return entries.findByJournalIdOrderByCreatedAtAsc(id); }

    private Map<String, LedgerAccount> resolve(List<JournalEntryRequest> lines) {
        Map<String, LedgerAccount> result = new HashMap<>();
        for (JournalEntryRequest line : lines) {
            LedgerAccount account = result.computeIfAbsent(line.getAccountCode(), code -> accounts.findByAccountCode(code)
                    .orElseThrow(() -> new LedgerAccountNotFoundException(code)));
            if (!account.isActive()) throw invalid("ledger account is not active");
            if (!account.getAsset().equals(line.getAsset())) throw invalid("LEDGER_ACCOUNT_ASSET_MISMATCH");
        }
        return result;
    }
    private static void validate(PostJournalRequest request) {
        if (request == null) throw invalid("journal is required");
        identifier(request.getPaymentId(), "paymentId"); identifier(request.getCorrelationId(), "correlationId");
        if (request.getJournalType() == null || !request.getJournalType().matches("[A-Z][A-Z0-9_]{0,49}"))
            throw invalid("journalType is required and must be a valid identifier");
        if (request.getReference() != null && request.getReference().length() > 100) throw invalid("reference is too long");
        if (request.getDescription() != null && request.getDescription().length() > 255) throw invalid("description is too long");
        if (request.getEntries() == null || request.getEntries().size() < 2 || request.getEntries().size() > 100)
            throw invalid("journal must contain at least two entries and at most 100");
        // New private assets always require network-qualified provenance; public validation is unchanged.
        boolean privateAsset=request.getEntries().stream().filter(Objects::nonNull)
                .anyMatch(line->Set.of("DEMOUSD","BESU_GAS").contains(Objects.toString(line.getAsset(),"")));
        if(privateAsset) {
            var evidence=request.getEvidence();
            if(evidence==null || evidence.privateSettlement()==null ||
                    (evidence.mode()!=com.cognizant.stablecoins.ledger.accounting.PostingEvidence.Mode.PRIVATE_DEMO &&
                     evidence.mode()!=com.cognizant.stablecoins.ledger.accounting.PostingEvidence.Mode.SIMULATED))
                throw invalid("PRIVATE_ASSET_PROVENANCE_REQUIRED");
            var execution=com.cognizant.stablecoins.flow.SettlementBindingJson.read(evidence.privateSettlement().executionDocument(),
                    com.cognizant.stablecoins.flow.SettlementBinding.Execution.class);
            if(!execution.instruction().paymentId().equals(request.getPaymentId()) ||
                    !execution.instruction().correlationId().equals(request.getCorrelationId()))
                throw invalid("PRIVATE_ASSET_PAYMENT_SCOPE_MISMATCH");
            String asset=request.getEntries().stream().filter(Objects::nonNull).map(JournalEntryRequest::getAsset)
                    .filter(a->"DEMOUSD".equals(a)||"BESU_GAS".equals(a)).findFirst().orElseThrow();
            if(request.getEntries().stream().filter(Objects::nonNull).anyMatch(line->!asset.equals(line.getAsset())))
                throw invalid("PRIVATE_ASSET_JOURNAL_MIXED_ASSETS");
        }
        for (JournalEntryRequest line : request.getEntries()) {
            if (line == null || line.getAccountCode() == null || !line.getAccountCode().matches("[A-Za-z0-9]{1,10}"))
                throw invalid("accountCode is required");
            try { SupportedAsset.valueOf(line.getAsset()); } catch (IllegalArgumentException | NullPointerException e) { throw invalid("unsupported asset"); }
            BigDecimal dr = zero(line.getDebit()), cr = zero(line.getCredit());
            if (dr.signum() < 0) throw invalid("debit cannot be negative");
            if (cr.signum() < 0) throw invalid("credit cannot be negative");
            if (dr.signum() > 0 && cr.signum() > 0) throw invalid("entry cannot have both debit and credit > 0");
            if (dr.signum() == 0 && cr.signum() == 0) throw invalid("entry must have one positive side");
            for (BigDecimal value : List.of(dr, cr)) {
                BigDecimal normalized = value.stripTrailingZeros();
                if (normalized.scale() > ("DEMOUSD".equals(line.getAsset())?6:18) || normalized.precision() - normalized.scale() > 20)
                    throw invalid("AMOUNT_PRECISION_EXCEEDED");
            }
        }
    }
    private static void balance(List<JournalEntryRequest> lines) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (var line : lines) totals.merge(line.getAsset(), zero(line.getDebit()).subtract(zero(line.getCredit())), BigDecimal::add);
        totals.forEach((asset, total) -> { if (total.signum() != 0) throw invalid("journal is not balanced for asset " + asset); });
    }
    private String legacyFingerprint(Journal journal) {
        List<String> lines = entries.findByJournalIdOrderByCreatedAtAsc(journal.getJournalId()).stream()
                .map(line -> canonicalLine(line.getLedgerAccountId(), line.getAsset(), line.getDebit(), line.getCredit())).sorted().toList();
        if (lines.isEmpty()) throw invalid("LEGACY_JOURNAL_REVIEW_REQUIRED");
        return digest(journal.getPaymentId(), journal.getCorrelationId(), journal.getJournalType(), journal.getReference(),
                journal.getDescription(), journal.getEvidence(), lines);
    }
    private static String fingerprint(PostJournalRequest request, Map<String, LedgerAccount> resolved) {
        List<String> lines = request.getEntries().stream().map(line -> canonicalLine(resolved.get(line.getAccountCode()).getLedgerAccountId(),
                line.getAsset(), line.getDebit(), line.getCredit())).sorted().toList();
        return digest(request.getPaymentId(), request.getCorrelationId(), request.getJournalType(), request.getReference(),
                request.getDescription(), request.getEvidence(), lines);
    }
    private static String canonicalLine(UUID account, String asset, BigDecimal dr, BigDecimal cr) {
        return account + ":" + asset + ":" + zero(dr).stripTrailingZeros().toPlainString() + ":" + zero(cr).stripTrailingZeros().toPlainString();
    }
    private static String digest(Object... values) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsString(values).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("LEDGER_FINGERPRINT_FAILED"); }
    }
    private static BigDecimal zero(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }
    private static void identifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) throw invalid(field + " is required and must be a valid identifier");
    }
    private static JournalValidationException invalid(String message) { return new JournalValidationException(message); }
}
