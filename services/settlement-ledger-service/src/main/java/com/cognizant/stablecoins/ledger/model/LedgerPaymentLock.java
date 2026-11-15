package com.cognizant.stablecoins.ledger.model;

import jakarta.persistence.*;

/** Ledger-owned serialization boundary, independent of payment-service tables. */
@Entity
@Table(name = "ledger_payment_lock")
public class LedgerPaymentLock {
    @Id @Column(name = "payment_id", length = 64) private String paymentId;
    @Column(name = "correlation_id", nullable = false, length = 64) private String correlationId;
    protected LedgerPaymentLock() {}
    public LedgerPaymentLock(String paymentId, String correlationId) { this.paymentId=paymentId; this.correlationId=correlationId; }
    public String getCorrelationId() { return correlationId; }
}
