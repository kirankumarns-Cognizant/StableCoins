package com.cognizant.stablecoins.payment.repository;

import com.cognizant.stablecoins.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.paymentId = :paymentId")
    Optional<Payment> lockByPaymentId(@org.springframework.data.repository.query.Param("paymentId") String paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
