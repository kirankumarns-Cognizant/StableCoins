package com.cognizant.stablecoins.visa.liquidity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface VisaLiquidityAuditRepository extends JpaRepository<VisaLiquidityAudit,UUID> {}
