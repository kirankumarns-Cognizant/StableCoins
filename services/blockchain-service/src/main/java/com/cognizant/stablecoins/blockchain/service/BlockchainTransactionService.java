package com.cognizant.stablecoins.blockchain.service;

import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import com.cognizant.stablecoins.blockchain.enums.TransactionStatus;
import com.cognizant.stablecoins.blockchain.event.*;
import com.cognizant.stablecoins.blockchain.model.*;
import com.cognizant.stablecoins.blockchain.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import java.math.*;
import java.time.*;
import java.util.*;

/** Deterministic execution only. Never register mutation methods as model tools. */
@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class BlockchainTransactionService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(BlockchainTransactionService.class);
    private final BlockchainAdapter adapter;
    private final WalletSigningService signingService;
    private final BlockchainTransactionRepository txRepository;
    private final BlockchainWalletRepository walletRepository;
    private final BlockchainOutboxEventRepository outboxRepository;
    private final BlockchainExecutionAuditRepository auditRepository;
    private final BlockchainProperties props;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate database;
    // Each call polls once by default; no database transaction spans polling or broadcasting.
    int maxConfirmationPolls=1;
    long pollIntervalMs=0;

    public BlockchainTransactionService(BlockchainAdapter adapter, WalletSigningService signingService,
            BlockchainTransactionRepository txRepository, BlockchainWalletRepository walletRepository,
            BlockchainOutboxEventRepository outboxRepository, BlockchainProperties props,
            ObjectMapper objectMapper, BlockchainExecutionAuditRepository auditRepository,
            PlatformTransactionManager transactionManager) {
        this.adapter=adapter;this.signingService=signingService;this.txRepository=txRepository;
        this.walletRepository=walletRepository;this.outboxRepository=outboxRepository;
        this.props=props;this.objectMapper=objectMapper;this.auditRepository=auditRepository;
        database=new TransactionTemplate(transactionManager);
        database.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Read-only evidence; current balances minus effective holds, never an execution authorization. */
    public TreasuryReadiness getReadiness(String toAddress,BigDecimal amount) {
        validateTransfer(toAddress,amount);
        return database.execute(s -> {
            String from=sender();
            // Lock an existing owned wallet so local reservations and this observation are consistent.
            walletRepository.lockWallet(from,props.getNetwork());
            return observe(from,toAddress,amount,null);
        });
    }

    /** Atomically holds both USDC and gas ETH using the existing wallet as serialization point. */
    public BlockchainTransaction prepareTransfer(String paymentId,String correlationId,String toAddress,BigDecimal amount) {
        requireIdentifier(paymentId);requireIdentifier(correlationId);validateTransfer(toAddress,amount);
        return database.execute(s -> {
            String from=sender();
            walletRepository.ensureWallet(UUID.randomUUID(),from,props.getNetwork());
            lockWallet(from,props.getNetwork());
            var existing=txRepository.findByPaymentId(paymentId);
            if(existing.isPresent()) {
                var tx=existing.get();
                if(!Objects.equals(correlationId,tx.getCorrelationId()) || !toAddress.equalsIgnoreCase(tx.getToAddress())
                        || amount.compareTo(tx.getAmount())!=0 || !props.getNetwork().equals(tx.getNetwork())
                        || !props.getTokenContract().equalsIgnoreCase(tx.getTokenContract()))
                    throw new IllegalStateException("PAYMENT_OPERATION_CONFLICT");
                expire(tx,Instant.now());
                return tx; // Includes prepared, failed, expired and uncertain operations: never recreate a leg.
            }
            expireHeld(from,props.getNetwork());
            var readiness=observe(from,toAddress,amount,null);
            requireReady(readiness);
            var tx=new BlockchainTransaction();
            tx.setTransactionId(UUID.randomUUID());tx.setPaymentId(paymentId);tx.setCorrelationId(correlationId);
            tx.setNetwork(props.getNetwork());tx.setChainId(props.getChainId());tx.setFromAddress(from);
            tx.setToAddress(toAddress.toLowerCase(Locale.ROOT));tx.setTokenContract(props.getTokenContract());
            tx.setTokenSymbol(props.getTokenSymbol());tx.setAmount(amount);
            tx.setAmountInSmallest(amount.movePointRight(props.getTokenDecimals()));
            applyReadiness(tx,readiness);tx.setStatus("PREPARED");tx.setReservationStatus("HELD");
            tx.setReservationExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plus(props.getReservationTtl()));
            return save(tx,"RESERVED");
        });
    }

    /** Commit PINNED + nonce + expected hash BEFORE the single broadcast attempt. */
    public BlockchainTransaction submitTransaction(UUID transactionId) {
        Claim claim=database.execute(s -> {
            var tx=lockTransaction(transactionId);
            if(!"PREPARED".equals(tx.getStatus())) return new Claim(tx,null);
            if(expire(tx,Instant.now())) return new Claim(tx,null);
            if(!"HELD".equals(tx.getReservationStatus())) throw new IllegalStateException("RESERVATION_REVIEW_REQUIRED");
            validateStoredConfiguration(tx);
            expireHeld(tx.getFromAddress(),tx.getNetwork());
            var readiness=observe(tx.getFromAddress(),tx.getToAddress(),tx.getAmount(),transactionId);
            requireReady(readiness); // Revalidate price, gas and balances, excluding only this operation's hold.
            applyReadiness(tx,readiness);
            if(expire(tx,Instant.now())) return new Claim(tx,null);
            var wallet=lockWallet(tx.getFromAddress(),tx.getNetwork());
            long nonce=Math.max(wallet.getPendingNonce(),adapter.getPendingNonce(tx.getFromAddress()));
            if(nonce<0 || nonce==Long.MAX_VALUE) throw new IllegalStateException("INVALID_NONCE");
            byte[] signed=adapter.signRawTransaction(nonce,tx.getToAddress(),tx.getTokenContract(),
                    tx.getAmount(),props.getTokenDecimals(),tx.getGasLimit(),tx.getGasPriceWei());
            if(signed==null || signed.length==0) throw new IllegalStateException("SIGNING_FAILED");
            tx.setNonce(nonce);tx.setTxHash(Numeric.toHexString(Hash.sha3(signed)));
            tx.setExplorerUrl(props.getExplorerBaseUrl()+"/tx/"+tx.getTxHash());
            tx.setStatus("SUBMITTING");tx.setReservationStatus("PINNED");
            // This nonce is claimed even if the process stops before/after broadcasting.
            wallet.setPendingNonce(nonce+1);walletRepository.save(wallet);
            save(tx,"SUBMISSION_CLAIMED");
            return new Claim(tx,signed);
        });
        if(claim.signed()==null) return claim.tx();
        boolean acknowledged=false;
        try {
            String actualHash=adapter.submitRawTransaction(claim.signed());
            acknowledged=claim.tx().getTxHash().equalsIgnoreCase(actualHash);
        } catch(Exception ignored) {
            // Never persist/log raw RPC error messages: providers can include credential-bearing URLs.
        }
        final boolean accepted=acknowledged;
        return database.execute(s -> {
            var tx=lockTransaction(transactionId);
            if(!"SUBMITTING".equals(tx.getStatus())) return tx;
            tx.setStatus(accepted ? "SUBMITTED" : "SUBMISSION_UNKNOWN");
            tx.setSubmittedAt(OffsetDateTime.now(ZoneOffset.UTC));
            tx.setFailureReason(accepted ? null : "SUBMISSION_OUTCOME_UNRESOLVED");
            save(tx,accepted ? "BROADCAST_ACKNOWLEDGED" : "BROADCAST_UNRESOLVED");
            if(accepted) appendOutboxEvent(tx,"BlockchainTransactionSubmitted");
            return tx;
        }); // DB failure here leaves the already committed claim available for receipt recovery.
    }

    /** Expiry/release is permitted only while unsigned. A payment key is never made reusable. */
    public BlockchainTransaction releaseReservation(UUID transactionId) {
        return database.execute(s -> {
            var tx=lockTransaction(transactionId);
            if("RELEASED".equals(tx.getReservationStatus()) || "EXPIRED".equals(tx.getReservationStatus())) return tx;
            if(!"PREPARED".equals(tx.getStatus()) || !"HELD".equals(tx.getReservationStatus()))
                throw new IllegalStateException("SIGNED_OPERATION_REQUIRES_RECONCILIATION");
            tx.setStatus("FAILED");tx.setReservationStatus("RELEASED");tx.setFailureReason("RESERVATION_RELEASED");
            return save(tx,"RELEASED_BEFORE_SIGNING");
        });
    }

    /** Missing/late receipts remain unresolved and retain funds. Only a matching final receipt releases. */
    public BlockchainTransaction confirmTransaction(UUID transactionId) {
        var current=txRepository.findById(transactionId).orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if(!Set.of("SUBMITTING","SUBMITTED","SUBMISSION_UNKNOWN").contains(current.getStatus())) return current;
        validateStoredConfiguration(current);
        for(int attempt=0;attempt<maxConfirmationPolls;attempt++) {
            var receipt=adapter.getReceipt(current.getTxHash());
            if(receipt.isPresent()) {
                var r=receipt.get();
                if(!current.getTxHash().equalsIgnoreCase(r.getTxHash()) || r.getBlockNumber()==null || r.getBlockNumber()<0
                        || r.getGasUsed()==null || r.getGasUsed().signum()<0)
                    throw new BlockchainRpcException("RECEIPT_EVIDENCE_MISMATCH");
                long head=adapter.getCurrentBlockNumber();
                if(head>=r.getBlockNumber() && head-r.getBlockNumber()+1>=props.getConfirmationsRequired()) {
                    return database.execute(s -> {
                        var tx=lockTransaction(transactionId);
                        if(Set.of("CONFIRMED","REVERTED").contains(tx.getStatus())) return tx;
                        // A matching mined receipt proves submission even if the RPC acknowledgement was lost.
                        // Publish the missing predecessor atomically; ledger delivery may still be reordered and retried.
                        if(Set.of("SUBMITTING","SUBMISSION_UNKNOWN").contains(tx.getStatus())) appendOutboxEvent(tx,"BlockchainTransactionSubmitted");
                        tx.setStatus(r.isSuccess() ? "CONFIRMED" : "REVERTED");tx.setReservationStatus("SETTLED");
                        tx.setBlockNumber(r.getBlockNumber());tx.setGasUsed(r.getGasUsed());
                        tx.setEffectiveGasPrice(r.getEffectiveGasPrice());
                        tx.setAdditionalFeeWei(r.getAdditionalFeeWei()==null?null:new BigDecimal(r.getAdditionalFeeWei()));
                        tx.setTotalFeeWei(r.getTotalFeeWei()==null?null:new BigDecimal(r.getTotalFeeWei()));
                        tx.setFeeComponentsComplete(r.isFeeComponentsComplete());tx.setFeeEvidenceReason(r.getFeeEvidenceReason());
                        tx.setConfirmationCount((int)Math.min(Integer.MAX_VALUE,head-r.getBlockNumber()+1));
                        tx.setConfirmedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        tx.setFailureReason(r.isSuccess() ? null : "TRANSACTION_REVERTED");
                        save(tx,r.isSuccess() ? "RECEIPT_CONFIRMED" : "RECEIPT_REVERTED");
                        if(r.isSuccess()) appendOutboxEvent(tx,"BlockchainTransactionConfirmed");
                        if(tx.isFeeComponentsComplete()) appendReceiptEvent(tx);
                        return tx;
                    });
                }
            }
            if(attempt+1<maxConfirmationPolls && pollIntervalMs>0) {
                try { Thread.sleep(pollIntervalMs); } catch(InterruptedException e) { Thread.currentThread().interrupt();break; }
            }
        }
        return txRepository.findById(transactionId).orElseThrow();
    }

    private TreasuryReadiness observe(String from,String to,BigDecimal amount,UUID exclude) {
        validateNetwork();
        BigDecimal estimated=positiveInteger(adapter.estimateTransferGas(from,to,props.getTokenContract(),amount,props.getTokenDecimals()));
        BigDecimal gasLimit=estimated.multiply(props.getGasLimitMultiplier()).setScale(0,RoundingMode.CEILING);
        BigDecimal price=positiveInteger(adapter.getGasPriceWei()).multiply(props.getGasPriceMultiplier()).setScale(0,RoundingMode.CEILING);
        BigDecimal gasEth=gasLimit.multiply(price).movePointLeft(18).add(props.getAdditionalGasReserveEth());
        BigDecimal tokens=nonnegative(adapter.getTokenBalance(from,props.getTokenContract(),props.getTokenDecimals()));
        BigDecimal eth=nonnegative(adapter.getNativeBalance(from));
        BigDecimal reservedTokens=BigDecimal.ZERO,reservedEth=BigDecimal.ZERO;
        var reasons=new ArrayList<String>();var now=Instant.now();
        for(var tx:txRepository.findByFromAddressAndNetwork(from,props.getNetwork())) {
            if(Objects.equals(exclude,tx.getTransactionId())) continue;
            String reservation=tx.getReservationStatus();
            if("LEGACY_REVIEW".equals(reservation) && !Set.of("CONFIRMED","REVERTED").contains(tx.getStatus()))
                reasons.add("LEGACY_OPERATION_REQUIRES_REVIEW");
            boolean pinned="PINNED".equals(reservation);
            boolean held="HELD".equals(reservation) && tx.getReservationExpiresAt()!=null
                    && tx.getReservationExpiresAt().toInstant().isAfter(now);
            if(pinned || held) {
                if(!props.getTokenContract().equalsIgnoreCase(tx.getTokenContract()) || tx.getGasReserveEth()==null)
                    throw new IllegalStateException("RESERVATION_CONFIGURATION_CONFLICT");
                reservedTokens=reservedTokens.add(tx.getAmount());reservedEth=reservedEth.add(tx.getGasReserveEth());
            }
            // Serial execution per treasury wallet: an unresolved leg cannot be bypassed by another payment.
            if(pinned) reasons.add("WALLET_SUBMISSION_UNRESOLVED");
        }
        BigDecimal availableTokens=tokens.subtract(reservedTokens).max(BigDecimal.ZERO);
        BigDecimal availableEth=eth.subtract(reservedEth).max(BigDecimal.ZERO);
        if(availableTokens.compareTo(amount)<0) reasons.add("INSUFFICIENT_USDC");
        if(availableEth.compareTo(gasEth)<0) reasons.add("INSUFFICIENT_GAS_ETH");
        return new TreasuryReadiness(adapter.getEvidenceMode(),props.getNetwork(),props.getChainId(),props.getTokenContract(),
                from,tokens,reservedTokens,availableTokens,eth,reservedEth,availableEth,estimated,gasLimit,price,gasEth,
                now,now.plus(props.getReadinessTtl()),reasons.stream().distinct().toList());
    }
    private void requireReady(TreasuryReadiness r) {
        if(r.reasons().contains("INSUFFICIENT_USDC")) throw new InsufficientTokenBalanceException("INSUFFICIENT_USDC_AFTER_RESERVATIONS");
        if(r.reasons().contains("INSUFFICIENT_GAS_ETH")) throw new InsufficientGasException("INSUFFICIENT_GAS_ETH_AFTER_RESERVATIONS");
        if(!r.ready()) throw new IllegalStateException(String.join(",",r.reasons()));
    }
    private void applyReadiness(BlockchainTransaction tx,TreasuryReadiness r) {
        tx.setEstimatedGas(r.estimatedGas());tx.setGasLimit(r.gasLimit());tx.setGasPriceWei(r.gasPriceWei());
        tx.setGasReserveEth(r.requiredGasEth());tx.setEvidenceMode(r.evidenceMode());
    }
    private BlockchainWallet lockWallet(String from,String network) {
        return walletRepository.lockWallet(from,network).orElseThrow(() -> new IllegalStateException("OWNED_WALLET_REQUIRED"));
    }
    private BlockchainTransaction lockTransaction(UUID id) {
        var observed=txRepository.findWalletLocation(id).orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        lockWallet(observed.getFromAddress(),observed.getNetwork()); // Consistent lock order: wallet, then operation.
        return txRepository.findForUpdate(id).orElseThrow();
    }
    private void expireHeld(String from,String network) {
        for(var tx:txRepository.findByFromAddressAndNetwork(from,network)) expire(tx,Instant.now());
    }
    private boolean expire(BlockchainTransaction tx,Instant now) {
        if("HELD".equals(tx.getReservationStatus()) && "PREPARED".equals(tx.getStatus())
                && tx.getReservationExpiresAt()!=null && !tx.getReservationExpiresAt().toInstant().isAfter(now)) {
            tx.setStatus("FAILED");tx.setReservationStatus("EXPIRED");tx.setFailureReason("UNSIGNED_RESERVATION_EXPIRED");
            save(tx,"EXPIRED_BEFORE_SIGNING");return true;
        }
        return false;
    }
    private BlockchainTransaction save(BlockchainTransaction tx,String reason) {
        txRepository.save(tx);auditRepository.save(new BlockchainExecutionAudit(tx,reason));
        log.info("service=blockchain-service paymentId={} correlationId={} status={} reservationStatus={} txHash={} reason={}",
                tx.getPaymentId(),tx.getCorrelationId(),tx.getStatus(),tx.getReservationStatus(),tx.getTxHash(),reason);
        return tx;
    }
    private String sender() {
        String address=signingService.getWalletAddress();
        if(address==null || !address.matches("0x[0-9a-fA-F]{40}")) throw new BlockchainConfigurationException("INVALID_TREASURY_ADDRESS");
        return address.toLowerCase(Locale.ROOT);
    }
    private void validateStoredConfiguration(BlockchainTransaction tx) {
        validateNetwork();
        if(!props.getNetwork().equals(tx.getNetwork()) || !Objects.equals(tx.getChainId(),props.getChainId())
                || !props.getTokenContract().equalsIgnoreCase(tx.getTokenContract()) || !sender().equals(tx.getFromAddress())
                || !Objects.equals(adapter.getEvidenceMode(),tx.getEvidenceMode()))
            throw new BlockchainConfigurationException("STORED_EXECUTION_CONFIGURATION_MISMATCH");
    }
    private void validateNetwork() {
        boolean supported=("base-sepolia".equals(props.getNetwork()) && props.getChainId()==84532L)
                || ("ethereum-sepolia".equals(props.getNetwork()) && props.getChainId()==11155111L);
        if(!supported || !"USDC".equals(props.getTokenSymbol()) || props.getTokenDecimals()!=6
                || props.getTokenContract()==null || !props.getTokenContract().matches("0x[0-9a-fA-F]{40}")
                || props.getConfirmationsRequired()<1 || adapter.getChainId()!=props.getChainId())
            throw new BlockchainConfigurationException("UNSUPPORTED_OR_MISMATCHED_TESTNET_CONFIGURATION");
        if(!Set.of("LIVE","MOCK").contains(adapter.getEvidenceMode()))
            throw new BlockchainConfigurationException("UNKNOWN_ADAPTER_EVIDENCE_MODE");
    }
    private void validateTransfer(String to,BigDecimal amount) {
        if(to==null || !to.matches("0x[0-9a-fA-F]{40}") || to.equalsIgnoreCase("0x"+"0".repeat(40)))
            throw new BlockchainConfigurationException("INVALID_RECIPIENT_ADDRESS");
        if(amount==null || amount.signum()<=0 || amount.scale()>6 || amount.precision()-amount.scale()>12)
            throw new BlockchainConfigurationException("INVALID_USDC_AMOUNT");
    }
    private static void requireIdentifier(String id) {
        if(id==null || !id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) throw new IllegalArgumentException("INVALID_PAYMENT_IDENTIFIER");
    }
    private static BigDecimal positiveInteger(BigDecimal value) {
        if(value==null || value.signum()<=0 || value.stripTrailingZeros().scale()>0 || value.precision()>30)
            throw new BlockchainRpcException("INVALID_GAS_OBSERVATION");
        return value;
    }
    private static BigDecimal nonnegative(BigDecimal value) {
        if(value==null || value.signum()<0) throw new BlockchainRpcException("INVALID_BALANCE_OBSERVATION");return value;
    }
    public BlockchainTransaction refreshReceiptEvidence(UUID id) {
        var current=txRepository.findById(id).orElseThrow();validateStoredConfiguration(current);
        if(!Set.of("CONFIRMED","REVERTED").contains(current.getStatus()))throw new IllegalStateException("MINED_RECEIPT_REQUIRED");
        if(current.isFeeComponentsComplete())return current;
        var r=adapter.getReceipt(current.getTxHash()).orElseThrow(()->new BlockchainRpcException("RECEIPT_UNAVAILABLE"));
        if(!current.getTxHash().equalsIgnoreCase(r.getTxHash())||!Objects.equals(current.getBlockNumber(),r.getBlockNumber())||current.getGasUsed()==null||r.getGasUsed()==null||r.getEffectiveGasPrice()==null||current.getGasUsed().compareTo(r.getGasUsed())!=0||(current.getEffectiveGasPrice()!=null&&current.getEffectiveGasPrice().compareTo(r.getEffectiveGasPrice())!=0)||r.isSuccess()!=current.getStatus().equals("CONFIRMED"))throw new BlockchainRpcException("RECEIPT_EVIDENCE_MISMATCH");
        return database.execute(s->{var tx=lockTransaction(id);if(tx.isFeeComponentsComplete())return tx;
            tx.setEffectiveGasPrice(r.getEffectiveGasPrice());tx.setAdditionalFeeWei(r.getAdditionalFeeWei()==null?null:new BigDecimal(r.getAdditionalFeeWei()));tx.setTotalFeeWei(r.getTotalFeeWei()==null?null:new BigDecimal(r.getTotalFeeWei()));tx.setFeeComponentsComplete(r.isFeeComponentsComplete());tx.setFeeEvidenceReason(r.getFeeEvidenceReason());save(tx,"RECEIPT_FEE_REFRESHED");if(tx.isFeeComponentsComplete())appendReceiptEvent(tx);return tx;});
    }
    private void appendReceiptEvent(BlockchainTransaction tx) {
        var event=new DomainEvent<>(UUID.randomUUID(),"BlockchainReceiptObserved","1.0",tx.getPaymentId(),tx.getCorrelationId(),java.util.Map.of("transactionId",tx.getTransactionId()));
        try {var row=new BlockchainOutboxEvent();row.setEventId(event.getEventId());row.setPaymentId(tx.getPaymentId());row.setCorrelationId(tx.getCorrelationId());row.setEventType(event.getEventType());row.setPayload(objectMapper.writeValueAsString(event));outboxRepository.save(row);}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("OUTBOX_SERIALIZATION_FAILED");}
    }
    private void appendOutboxEvent(BlockchainTransaction tx,String type) {
        try {
            Object payload;
            if("BlockchainTransactionSubmitted".equals(type)) {
                var p=new BlockchainTransactionSubmittedPayload();p.setTransactionId(tx.getTransactionId());p.setNetwork(tx.getNetwork());
                p.setTxHash(tx.getTxHash());p.setFromAddress(tx.getFromAddress());p.setToAddress(tx.getToAddress());p.setAmount(tx.getAmount());
                p.setTokenSymbol(tx.getTokenSymbol());p.setExplorerUrl(tx.getExplorerUrl());p.setChainId(tx.getChainId());p.setTokenContract(tx.getTokenContract());p.setEvidenceMode(tx.getEvidenceMode());payload=p;
            } else {
                var p=new BlockchainTransactionConfirmedPayload();p.setTransactionId(tx.getTransactionId());p.setNetwork(tx.getNetwork());
                p.setTxHash(tx.getTxHash());p.setBlockNumber(tx.getBlockNumber());p.setFromAddress(tx.getFromAddress());p.setToAddress(tx.getToAddress());
                p.setAmount(tx.getAmount());p.setTokenSymbol(tx.getTokenSymbol());p.setExplorerUrl(tx.getExplorerUrl());p.setChainId(tx.getChainId());p.setTokenContract(tx.getTokenContract());p.setEvidenceMode(tx.getEvidenceMode());
                p.setGasUsed(tx.getGasUsed().toPlainString());p.setEffectiveGasPrice(tx.getEffectiveGasPrice()==null ? null : tx.getEffectiveGasPrice().toPlainString());payload=p;
            }
            UUID id=UUID.randomUUID();
            var event=new DomainEvent<>(id,type,"1.0",tx.getPaymentId(),tx.getCorrelationId(),payload);
            var row=new BlockchainOutboxEvent();row.setEventId(id);row.setPaymentId(tx.getPaymentId());row.setCorrelationId(tx.getCorrelationId());
            row.setEventType(type);row.setPayload(objectMapper.writeValueAsString(event));outboxRepository.save(row);
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("OUTBOX_SERIALIZATION_FAILED",e); }
    }
    private record Claim(BlockchainTransaction tx,byte[] signed) {}
}
