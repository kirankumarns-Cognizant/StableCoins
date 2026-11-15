package com.cognizant.stablecoins.blockchain.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="blockchain_execution_audit")
public class BlockchainExecutionAudit {
    @Id public UUID auditId;
    @Column(nullable=false) public UUID transactionId;
    @Column(nullable=false,length=64) public String paymentId;
    @Column(nullable=false,length=64) public String correlationId;
    @Column(nullable=false) public String status;
    @Column(nullable=false) public String reservationStatus;
    @Column(nullable=false) public String reason;
    @Column(nullable=false) public OffsetDateTime timestamp;
    protected BlockchainExecutionAudit() {}
    public BlockchainExecutionAudit(BlockchainTransaction tx,String reason) {
        auditId=UUID.randomUUID();transactionId=tx.getTransactionId();paymentId=tx.getPaymentId();
        correlationId=tx.getCorrelationId();status=tx.getStatus();reservationStatus=tx.getReservationStatus();
        this.reason=reason;timestamp=OffsetDateTime.now();
    }
}
