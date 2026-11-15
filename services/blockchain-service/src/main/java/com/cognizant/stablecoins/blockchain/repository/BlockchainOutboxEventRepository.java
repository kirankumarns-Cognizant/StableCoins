package com.cognizant.stablecoins.blockchain.repository;

import com.cognizant.stablecoins.blockchain.model.BlockchainOutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface BlockchainOutboxEventRepository extends JpaRepository<BlockchainOutboxEvent, UUID> {

    /**
     * Returns PENDING outbox rows in creation order, locked with SKIP LOCKED so that
     * concurrent dispatcher instances each claim a disjoint set of rows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM BlockchainOutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<BlockchainOutboxEvent> findPendingForUpdate();
}
