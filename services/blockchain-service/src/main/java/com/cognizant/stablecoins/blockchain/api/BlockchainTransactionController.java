package com.cognizant.stablecoins.blockchain.api;

import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import com.cognizant.stablecoins.blockchain.model.BlockchainTransaction;
import com.cognizant.stablecoins.blockchain.repository.BlockchainTransactionRepository;
import com.cognizant.stablecoins.blockchain.service.BlockchainTransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="blockchain.execution-api-enabled",havingValue="true")
@RestController
@RequestMapping("/api/v1/blockchain")
public class BlockchainTransactionController {

    private final com.cognizant.stablecoins.platform.security.PaymentOwnership ownership;
    private final BlockchainTransactionService blockchainTransactionService;
    private final BlockchainTransactionRepository txRepository;
    private final BlockchainProperties props;

    public BlockchainTransactionController(BlockchainTransactionService blockchainTransactionService,
                                            BlockchainTransactionRepository txRepository,
                                            BlockchainProperties props,
                                            com.cognizant.stablecoins.platform.security.PaymentOwnership ownership) {
        this.ownership = ownership;
        this.blockchainTransactionService = blockchainTransactionService;
        this.txRepository = txRepository;
        this.props = props;
    }

    /**
     * Initiates a USDC transfer: prepare then submit in one call.
     */
    @PostMapping("/transfers")
    public ResponseEntity<BlockchainTransactionResponse> initiateTransfer(
            @RequestBody @Valid TransferRequest request) {
        // Prepare
        BlockchainTransaction tx = blockchainTransactionService.prepareTransfer(
            request.getPaymentId(),
            request.getCorrelationId(),
            request.getToAddress(),
            request.getAmount()
        );
        // Auto-submit unless already in a terminal state
        String status = tx.getStatus();
        if ("PREPARED".equals(status)) {
            tx = blockchainTransactionService.submitTransaction(tx.getTransactionId());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(BlockchainTransactionResponse.from(tx));
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<BlockchainTransactionResponse> getTransaction(
            @PathVariable UUID transactionId, org.springframework.security.core.Authentication authentication) {
        return txRepository.findById(transactionId)
            .map(tx -> { ownership.require(tx.getPaymentId(), authentication); return ResponseEntity.ok(BlockchainTransactionResponse.from(tx)); })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/payments/{paymentId}/transaction")
    public ResponseEntity<BlockchainTransactionResponse> getByPayment(
            @PathVariable String paymentId, org.springframework.security.core.Authentication authentication) {
        ownership.require(paymentId, authentication);
        return txRepository.findByPaymentIdAndNetwork(paymentId, props.getNetwork())
            .map(tx -> ResponseEntity.ok(BlockchainTransactionResponse.from(tx)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/transactions/{transactionId}/receipt-evidence")
    public ResponseEntity<BlockchainTransactionResponse> refreshReceipt(@PathVariable UUID transactionId){return ResponseEntity.ok(BlockchainTransactionResponse.from(blockchainTransactionService.refreshReceiptEvidence(transactionId)));}

    @PostMapping("/transactions/{transactionId}/confirm")
    public ResponseEntity<BlockchainTransactionResponse> triggerConfirmation(
            @PathVariable UUID transactionId) {
        BlockchainTransaction tx = blockchainTransactionService.confirmTransaction(transactionId);
        return ResponseEntity.ok(BlockchainTransactionResponse.from(tx));
    }
}
