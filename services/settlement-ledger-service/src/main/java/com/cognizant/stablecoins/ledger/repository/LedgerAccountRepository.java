package com.cognizant.stablecoins.ledger.repository;

import com.cognizant.stablecoins.ledger.model.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByAccountCode(String accountCode);
}
