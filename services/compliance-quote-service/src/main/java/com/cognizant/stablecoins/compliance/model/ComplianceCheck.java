package com.cognizant.stablecoins.compliance.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persisted compliance check result.
 * simulation=true is ALWAYS set — compliance is never real in this POC.
 */
@Entity
@Table(name = "compliance_check")
public class ComplianceCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "check_id")
    private UUID checkId;

    @Column(name = "payment_id", nullable = false, length = 50)
    private String paymentId;

    @Column(name = "correlation_id", nullable = false, length = 60)
    private String correlationId;

    @Column(name = "check_type", nullable = false, length = 50)
    private String checkType;

    @Column(name = "result", nullable = false, length = 10)
    private String result;

    @Column(name = "simulation", nullable = false)
    private boolean simulation = true;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    @PrePersist
    void prePersist() {
        if (checkedAt == null) checkedAt = OffsetDateTime.now();
    }

    public UUID getCheckId() { return checkId; }
    public void setCheckId(UUID checkId) { this.checkId = checkId; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public boolean isSimulation() { return simulation; }
    public void setSimulation(boolean simulation) { this.simulation = simulation; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(OffsetDateTime checkedAt) { this.checkedAt = checkedAt; }
}
