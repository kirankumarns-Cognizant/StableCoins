package com.cognizant.stablecoins.visa.liquidity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="visa_liquidity_audit")
public class VisaLiquidityAudit {
    @Id public UUID auditId;
    @Column(nullable=false) public UUID reservationId;
    @Column(nullable=false,length=64) public String paymentId;
    @Column(nullable=false,length=64) public String correlationId;
    @Column(nullable=false,length=20) public String status;
    @Column(nullable=false) public Instant timestamp;
    protected VisaLiquidityAudit() {}
    VisaLiquidityAudit(VisaLiquidityReservation r) {
        auditId=UUID.randomUUID();reservationId=r.reservationId;paymentId=r.paymentId;
        correlationId=r.correlationId;status=r.status;timestamp=r.updatedAt;
    }
}
