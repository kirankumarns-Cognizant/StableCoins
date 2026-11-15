package com.cognizant.stablecoins.compliance.repository;

import com.cognizant.stablecoins.compliance.model.ComplianceCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplianceCheckRepository extends JpaRepository<ComplianceCheck, UUID> {

    List<ComplianceCheck> findByPaymentIdOrderByCheckedAtAsc(String paymentId);
}
