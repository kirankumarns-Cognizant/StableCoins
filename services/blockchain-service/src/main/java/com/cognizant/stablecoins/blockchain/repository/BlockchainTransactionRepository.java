package com.cognizant.stablecoins.blockchain.repository;
import com.cognizant.stablecoins.blockchain.model.BlockchainTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, UUID> {
    Optional<BlockchainTransaction> findByPaymentIdAndNetwork(String paymentId, String network);
    Optional<BlockchainTransaction> findByPaymentId(String paymentId);
    Optional<BlockchainTransaction> findByTxHash(String txHash);
    List<BlockchainTransaction> findByStatus(String status);
    List<BlockchainTransaction> findByFromAddressAndNetwork(String fromAddress, String network);
    interface WalletLocation { String getFromAddress(); String getNetwork(); }
    @Query("select t.fromAddress as fromAddress,t.network as network from BlockchainTransaction t where t.transactionId=:id")
    Optional<WalletLocation> findWalletLocation(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BlockchainTransaction t where t.transactionId = :id")
    Optional<BlockchainTransaction> findForUpdate(@Param("id") UUID id);
}
