package com.cognizant.stablecoins.payment.repository;

import com.cognizant.stablecoins.payment.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
}
