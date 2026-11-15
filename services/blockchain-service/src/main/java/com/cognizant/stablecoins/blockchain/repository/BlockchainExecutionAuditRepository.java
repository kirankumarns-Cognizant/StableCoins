package com.cognizant.stablecoins.blockchain.repository;
import com.cognizant.stablecoins.blockchain.model.BlockchainExecutionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface BlockchainExecutionAuditRepository extends JpaRepository<BlockchainExecutionAudit,UUID> {}
