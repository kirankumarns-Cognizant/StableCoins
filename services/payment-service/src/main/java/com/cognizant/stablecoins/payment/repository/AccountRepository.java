package com.cognizant.stablecoins.payment.repository;

import com.cognizant.stablecoins.payment.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Account a where a.accountId=:id")
    java.util.Optional<Account> lockByAccountId(@org.springframework.data.repository.query.Param("id") UUID id);
}
