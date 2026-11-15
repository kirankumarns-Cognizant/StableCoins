package com.cognizant.stablecoins.ledger.api.dto;

import java.util.List;

public class PostJournalRequest {
    private String paymentId;
    private String correlationId;
    private String journalType;
    private String reference;
    private String description;
    private List<JournalEntryRequest> entries;

    private com.cognizant.stablecoins.ledger.accounting.PostingEvidence evidence;
    public com.cognizant.stablecoins.ledger.accounting.PostingEvidence getEvidence() { return evidence; }
    public void setEvidence(com.cognizant.stablecoins.ledger.accounting.PostingEvidence evidence) { this.evidence = evidence; }

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

    public List<JournalEntryRequest> getEntries() { return entries; }
    public void setEntries(List<JournalEntryRequest> entries) { this.entries = entries; }
}
