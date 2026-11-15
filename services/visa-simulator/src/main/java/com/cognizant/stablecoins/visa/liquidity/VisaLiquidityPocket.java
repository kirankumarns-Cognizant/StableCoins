package com.cognizant.stablecoins.visa.liquidity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="visa_liquidity_pocket")
public class VisaLiquidityPocket {
    @Id @Column(length=100) public String pocketId;
    @Column(nullable=false,length=64) public String providerId;
    @Column(nullable=false,length=3) public String currency;
    @Column(nullable=false,precision=24,scale=2) public BigDecimal total;
    protected VisaLiquidityPocket() {}
}
