package com.cognizant.stablecoins.visa.liquidity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="visa_liquidity_funding")
public class VisaLiquidityFunding {
    @Id @Column(length=64) public String operationId;
    @Column(nullable=false,length=100) public String pocketId;
    @Column(nullable=false,precision=24,scale=2) public BigDecimal amount;
    @Column(nullable=false) public Instant timestamp;
    protected VisaLiquidityFunding() {}
}
