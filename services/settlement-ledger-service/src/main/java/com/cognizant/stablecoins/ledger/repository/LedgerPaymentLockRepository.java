package com.cognizant.stablecoins.ledger.repository;

import com.cognizant.stablecoins.ledger.model.LedgerPaymentLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface LedgerPaymentLockRepository extends JpaRepository<LedgerPaymentLock, String> {
    @Modifying
    @Query(value = "INSERT INTO ledger_payment_lock(payment_id,correlation_id) VALUES (:payment,:correlation) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensure(@Param("payment") String payment, @Param("correlation") String correlation);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LedgerPaymentLock l where l.paymentId = :payment")
    Optional<LedgerPaymentLock> lock(@Param("payment") String payment);
}
