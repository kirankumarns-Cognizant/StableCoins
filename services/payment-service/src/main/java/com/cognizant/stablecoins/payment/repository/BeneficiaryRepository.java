package com.cognizant.stablecoins.payment.repository;

import com.cognizant.stablecoins.payment.model.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
}
