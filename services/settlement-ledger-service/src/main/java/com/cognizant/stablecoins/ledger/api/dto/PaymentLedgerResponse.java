package com.cognizant.stablecoins.ledger.api.dto;

import java.util.List;

public class PaymentLedgerResponse {
    private String paymentId;
    private List<JournalResponse> journals;

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public List<JournalResponse> getJournals() { return journals; }
    public void setJournals(List<JournalResponse> journals) { this.journals = journals; }
}
