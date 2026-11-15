package com.cognizant.stablecoins.payment.approval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface RoutingAuditRepository extends JpaRepository<RoutingAudit, UUID> { }
