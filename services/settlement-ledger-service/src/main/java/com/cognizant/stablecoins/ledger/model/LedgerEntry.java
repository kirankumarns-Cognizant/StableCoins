package com.cognizant.stablecoins.ledger.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "journal_id", nullable = false)
    private UUID journalId;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Column(name = "payment_id", nullable = false, length = 64)
    private String paymentId;

    @Column(name = "asset", nullable = false, length = 10)
    private String asset;

    @Column(name = "debit", nullable = false, precision = 38, scale = 18)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", nullable = false, precision = 38, scale = 18)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "reversal_of")
    private UUID reversalOf;

    @PrePersist
    void prePersist() {
        if (entryId == null) entryId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (debit == null) debit = BigDecimal.ZERO;
        if (credit == null) credit = BigDecimal.ZERO;
    }

    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID entryId) { this.entryId = entryId; }

    public UUID getJournalId() { return journalId; }
    public void setJournalId(UUID journalId) { this.journalId = journalId; }

    public UUID getLedgerAccountId() { return ledgerAccountId; }
    public void setLedgerAccountId(UUID ledgerAccountId) { this.ledgerAccountId = ledgerAccountId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getAsset() { return asset; }
    public void setAsset(String asset) { this.asset = asset; }

    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal debit) { this.debit = debit; }

    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public UUID getReversalOf() { return reversalOf; }
    public void setReversalOf(UUID reversalOf) { this.reversalOf = reversalOf; }
}
