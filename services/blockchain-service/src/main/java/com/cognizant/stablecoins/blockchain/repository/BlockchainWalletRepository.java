package com.cognizant.stablecoins.blockchain.repository;
import com.cognizant.stablecoins.blockchain.model.BlockchainWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface BlockchainWalletRepository extends JpaRepository<BlockchainWallet, UUID> {
    Optional<BlockchainWallet> findByWalletAddressAndNetwork(String walletAddress, String network);
    @Modifying
    @Query(value = "insert into blockchain_wallet(wallet_id,wallet_address,network,pending_nonce) values (:id,:address,:network,0) on conflict (wallet_address,network) do nothing", nativeQuery = true)
    void ensureWallet(@Param("id") UUID id, @Param("address") String address, @Param("network") String network);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from BlockchainWallet w where w.walletAddress = :address and w.network = :network")
    Optional<BlockchainWallet> lockWallet(@Param("address") String address, @Param("network") String network);
}
