package com.cognizant.stablecoins.ledger.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class JournalResponse {
    private UUID journalId;
    private String paymentId;
    private String correlationId;
    private String journalType;
    private String reference;
    private String description;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime postedAt;
    private UUID reversedByJournalId;
    private List<LedgerEntryResponse> entries;

    private com.cognizant.stablecoins.ledger.accounting.PostingEvidence evidence;
    public com.cognizant.stablecoins.ledger.accounting.PostingEvidence getEvidence() { return evidence; }
    public void setEvidence(com.cognizant.stablecoins.ledger.accounting.PostingEvidence evidence) { this.evidence = evidence; }

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

    public List<LedgerEntryResponse> getEntries() { return entries; }
    public void setEntries(List<LedgerEntryResponse> entries) { this.entries = entries; }
}
