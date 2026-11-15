package com.cognizant.stablecoins.ledger.repository;

import com.cognizant.stablecoins.ledger.model.Journal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalRepository extends JpaRepository<Journal, UUID> {
    interface Scope { String getPaymentId(); String getCorrelationId(); }
    @org.springframework.data.jpa.repository.Query("select j.paymentId as paymentId, j.correlationId as correlationId from Journal j where j.journalId = :id")
    Optional<Scope> findScope(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select j from Journal j where j.journalId = :id")
    Optional<Journal> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
    List<Journal> findByPaymentIdOrderByCreatedAtAsc(String paymentId);
    Optional<Journal> findByPaymentIdAndJournalType(String paymentId, String journalType);
}
