package com.cognizant.stablecoins.ledger.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_journal")
public class Journal {

    @Id
    @Column(name = "journal_id")
    private UUID journalId;

    @Column(name = "payment_id", nullable = false, length = 64)
    private String paymentId;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "journal_type", nullable = false, length = 100)
    private String journalType;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

    @Column(name = "reversed_by_journal_id")
    private UUID reversedByJournalId;

    @PrePersist
    void prePersist() {
        if (journalId == null) journalId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")

    private com.cognizant.stablecoins.ledger.accounting.PostingEvidence evidence;
    public com.cognizant.stablecoins.ledger.accounting.PostingEvidence getEvidence() { return evidence; }
    public void setEvidence(com.cognizant.stablecoins.ledger.accounting.PostingEvidence evidence) { this.evidence = evidence; }

    @Column(name = "posting_fingerprint", length = 64)
    private String postingFingerprint;
    public String getPostingFingerprint() { return postingFingerprint; }
    public void setPostingFingerprint(String value) { postingFingerprint = value; }

    public UUID getJournalId() { return journalId; }
    public void setJournalId(UUID journalId) { this.journalId = journalId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getJournalType() { return journalType; }
    public void setJournalType(String journalType) { this.journalType = journalType; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(OffsetDateTime postedAt) { this.postedAt = postedAt; }

    public UUID getReversedByJournalId() { return reversedByJournalId; }
    public void setReversedByJournalId(UUID reversedByJournalId) { this.reversedByJournalId = reversedByJournalId; }
}
