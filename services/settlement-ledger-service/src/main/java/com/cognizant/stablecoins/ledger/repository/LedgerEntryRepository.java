package com.cognizant.stablecoins.ledger.repository;

import com.cognizant.stablecoins.ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByJournalIdOrderByCreatedAtAsc(UUID journalId);
}
