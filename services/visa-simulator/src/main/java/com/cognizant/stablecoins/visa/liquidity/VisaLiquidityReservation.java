package com.cognizant.stablecoins.visa.liquidity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="visa_liquidity_reservation")
public class VisaLiquidityReservation {
    @Id public UUID reservationId;
    @Column(nullable=false,length=64) public String paymentId;
    @Column(nullable=false,length=64) public String correlationId;
    @Column(nullable=false,length=100) public String pocketId;
    @Column(nullable=false,length=64) public String providerId;
    @Column(nullable=false,length=3) public String currency;
    @Column(nullable=false,precision=24,scale=2) public BigDecimal amount;
    @Column(nullable=false,length=20) public String status;
    @Column(nullable=false) public Instant expiresAt;
    @Column(nullable=false) public Instant createdAt;
    @Column(nullable=false) public Instant updatedAt;
    protected VisaLiquidityReservation() {}
    VisaPaymentAdapter.Reservation view() {
        return new VisaPaymentAdapter.Reservation(reservationId,paymentId,correlationId,providerId,
                VisaPaymentAdapter.Currency.valueOf(currency),amount,status,expiresAt,"SIMULATED");
    }
}
