package com.cognizant.stablecoins.payment.repository;

import com.cognizant.stablecoins.payment.model.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, UUID> {

    List<PaymentStatusHistory> findByPaymentIdOrderByTimestampAsc(String paymentId);
}
