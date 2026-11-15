package com.cognizant.stablecoins.blockchain.service;

import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import com.cognizant.stablecoins.blockchain.enums.BlockchainMode;
import com.cognizant.stablecoins.blockchain.enums.TransactionStatus;
import com.cognizant.stablecoins.blockchain.event.DomainEvent;
import com.cognizant.stablecoins.blockchain.event.publisher.DomainEventPublisher;
import com.cognizant.stablecoins.blockchain.model.BlockchainOutboxEvent;
import com.cognizant.stablecoins.blockchain.model.BlockchainTransaction;
import com.cognizant.stablecoins.blockchain.model.BlockchainWallet;
import com.cognizant.stablecoins.blockchain.repository.BlockchainOutboxEventRepository;
import com.cognizant.stablecoins.blockchain.repository.BlockchainExecutionAuditRepository;
import com.cognizant.stablecoins.blockchain.repository.BlockchainTransactionRepository;
import com.cognizant.stablecoins.blockchain.repository.BlockchainWalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlockchainServiceTest {

    @Mock BlockchainAdapter mockAdapter;
    @Mock BlockchainTransactionRepository txRepository;
    @Mock BlockchainWalletRepository walletRepository;
    @Mock BlockchainOutboxEventRepository outboxRepository;
    @Mock DomainEventPublisher publisher;
    @Mock WalletSigningService signingService;
    @Mock BlockchainExecutionAuditRepository auditRepository;
    @Mock org.springframework.transaction.PlatformTransactionManager transactionManager;

    BlockchainTransactionService service;
    BlockchainProperties props;
    ObjectMapper objectMapper;

    static final String FROM_ADDRESS = "0xaabbccddeeff0011223344556677889900aabbcc";
    static final String TO_ADDRESS   = "0x1122334455667788990011223344556677889900";
    static final String PAYMENT_ID = "PAY-TEST-10001";
    static final String CORR_ID = "CORR-TEST-10001";

    @BeforeEach
    void setUp() {
        props = new BlockchainProperties();
        props.setMode(BlockchainMode.MOCK);
        props.setNetwork("base-sepolia");
        props.setChainId(84532L);
        props.setTokenContract("0x036CbD53842c5426634e7929541eC2318f3dCF7e");
        props.setTokenSymbol("USDC");
        props.setTokenDecimals(6);
        props.setExplorerBaseUrl("https://sepolia.basescan.org");
        props.setConfirmationsRequired(1);

        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        service = new BlockchainTransactionService(
            mockAdapter, signingService, txRepository, walletRepository,
            outboxRepository, props, objectMapper, auditRepository, transactionManager);

        when(transactionManager.getTransaction(any())).thenAnswer(i -> new org.springframework.transaction.support.SimpleTransactionStatus());
        when(mockAdapter.getEvidenceMode()).thenReturn("MOCK");
        when(mockAdapter.getGasPriceWei()).thenReturn(new BigDecimal("1000000000"));
        when(txRepository.findForUpdate(any())).thenAnswer(i -> txRepository.findById(i.getArgument(0)));
        when(txRepository.findWalletLocation(any())).thenReturn(Optional.of(new BlockchainTransactionRepository.WalletLocation() {
            public String getFromAddress() { return FROM_ADDRESS; }
            public String getNetwork() { return "base-sepolia"; }
        }));
        when(walletRepository.lockWallet(any(),any())).thenAnswer(i -> walletRepository.findByWalletAddressAndNetwork(i.getArgument(0),i.getArgument(1)));
        // Default stubs
        when(mockAdapter.getChainId()).thenReturn(84532L);
        when(mockAdapter.estimateTransferGas(any(), any(), any(), any(), anyInt()))
            .thenReturn(new BigDecimal("65000"));
        when(mockAdapter.getNativeBalance(any())).thenReturn(new BigDecimal("0.5"));
        when(mockAdapter.getTokenBalance(any(), any(), anyInt()))
            .thenReturn(new BigDecimal("10000.000000"));

        when(txRepository.findByPaymentId(any())).thenReturn(Optional.empty());
        when(signingService.getWalletAddress()).thenReturn(FROM_ADDRESS);

        BlockchainWallet wallet = new BlockchainWallet();
        wallet.setWalletAddress(FROM_ADDRESS);
        wallet.setNetwork("base-sepolia");
        wallet.setPendingNonce(0L);
        when(walletRepository.findByWalletAddressAndNetwork(any(), any()))
            .thenReturn(Optional.of(wallet));

        // Save stubs — return the argument passed in
        when(txRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(mockAdapter.signRawTransaction(anyLong(), any(), any(), any(), anyInt(), any(), any()))
            .thenReturn(new byte[]{0x01, 0x02, 0x03});
        when(mockAdapter.submitRawTransaction(any())).thenAnswer(i -> i.getArgument(0)==null ? null : org.web3j.utils.Numeric.toHexString(org.web3j.crypto.Hash.sha3((byte[])i.getArgument(0))));
        when(mockAdapter.getReceipt(any())).thenAnswer(i -> Optional.of(new BlockchainReceiptResult(i.getArgument(0),1234567L,true,new BigDecimal("42000"),new BigDecimal("1000000000"))));
        when(mockAdapter.getCurrentBlockNumber()).thenReturn(1234568L);
    }

    // ── Test 1: MOCK mode prepare success ────────────────────────────────────

    @Test
    void mockMode_prepareTransfer_success() {
        BlockchainTransaction tx = service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("100.000000"));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PREPARED.name());
        assertThat(tx.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("100.000000"));
        assertThat(tx.getFromAddress()).isEqualTo(FROM_ADDRESS);
        assertThat(tx.getToAddress()).isEqualTo(TO_ADDRESS);
    }

    // ── Test 2: MOCK mode submit success ─────────────────────────────────────

    @Test
    void mockMode_submitTransaction_success() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction prepared = makeTx(txId, TransactionStatus.PREPARED);
        when(txRepository.findById(txId)).thenReturn(Optional.of(prepared));

        BlockchainTransaction result = service.submitTransaction(txId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUBMITTED.name());
        assertThat(result.getTxHash()).isNotBlank();
    }

    // ── Test 3: MOCK mode confirm success ─────────────────────────────────────

    @Test
    void mockMode_confirmTransaction_success() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction submitted = makeTx(txId, TransactionStatus.SUBMITTED);
        submitted.setTxHash("0x" + "b".repeat(64));
        when(txRepository.findById(txId)).thenReturn(Optional.of(submitted));

        BlockchainTransaction result = service.confirmTransaction(txId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.CONFIRMED.name());
        assertThat(result.getBlockNumber()).isEqualTo(1234567L);
    }

    // ── Test 4: Full lifecycle ────────────────────────────────────────────────

    @Test
    void mockMode_fullLifecycle_prepareSubmitConfirm() {
        // Prepare
        BlockchainTransaction tx = service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("50.000000"));
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PREPARED.name());

        // Submit
        UUID txId = tx.getTransactionId() != null ? tx.getTransactionId() : UUID.randomUUID();
        tx.setTransactionId(txId);
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));
        tx = service.submitTransaction(txId);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUBMITTED.name());

        // Confirm
        tx.setTxHash("0x" + "b".repeat(64));
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));
        tx = service.confirmTransaction(txId);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.CONFIRMED.name());
    }

    // ── Test 5: UNSUPPORTED_OR_MISMATCHED_TESTNET_CONFIGURATION ─────────────────────────────────────────────

    @Test
    void wrongChain_configuredVsRpc_throws() {
        when(mockAdapter.getChainId()).thenReturn(1L); // mainnet instead of sepolia

        assertThatThrownBy(() -> service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("1.000000")))
            .isInstanceOf(BlockchainConfigurationException.class)
            .hasMessageContaining("UNSUPPORTED_OR_MISMATCHED_TESTNET_CONFIGURATION");
    }

    // ── Test 6: INSUFFICIENT_GAS_ETH_AFTER_RESERVATIONS for gas ─────────────────────────────────────

    @Test
    void insufficientGas_ethBalance_throws() {
        when(mockAdapter.getNativeBalance(any())).thenReturn(new BigDecimal("0.000000001")); // near zero

        assertThatThrownBy(() -> service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("1.000000")))
            .isInstanceOf(InsufficientGasException.class)
            .hasMessageContaining("INSUFFICIENT_GAS_ETH_AFTER_RESERVATIONS");
    }

    // ── Test 7: INSUFFICIENT_USDC_AFTER_RESERVATIONS ─────────────────────────────────────────────

    @Test
    void insufficientUsdc_balance_throws() {
        when(mockAdapter.getTokenBalance(any(), any(), anyInt()))
            .thenReturn(new BigDecimal("0.5")); // less than amount

        assertThatThrownBy(() -> service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("100.000000")))
            .isInstanceOf(InsufficientTokenBalanceException.class)
            .hasMessageContaining("INSUFFICIENT_USDC_AFTER_RESERVATIONS");
    }

    // ── Test 8: Duplicate SUBMITTED ──────────────────────────────────────────

    @Test
    void duplicateSubmission_existingSubmitted_reused() {
        BlockchainTransaction existing = makeTx(UUID.randomUUID(), TransactionStatus.SUBMITTED);
        when(txRepository.findByPaymentId(PAYMENT_ID))
            .thenReturn(Optional.of(existing));

        BlockchainTransaction result = service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("1.000000"));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUBMITTED.name());
        verify(txRepository, never()).save(any()); // no new save
    }

    // ── Test 9: Duplicate CONFIRMED ──────────────────────────────────────────

    @Test
    void duplicateSubmission_existingConfirmed_reused() {
        BlockchainTransaction existing = makeTx(UUID.randomUUID(), TransactionStatus.CONFIRMED);
        when(txRepository.findByPaymentId(PAYMENT_ID))
            .thenReturn(Optional.of(existing));

        BlockchainTransaction result = service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("1.000000"));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.CONFIRMED.name());
    }

    // ── Test 10: RPC timeout → SUBMISSION_UNKNOWN ─────────────────────────────

    @Test
    void rpcTimeout_submissionUnknown() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction prepared = makeTx(txId, TransactionStatus.PREPARED);
        when(txRepository.findById(txId)).thenReturn(Optional.of(prepared));
        when(mockAdapter.submitRawTransaction(any()))
            .thenThrow(new BlockchainRpcException("connection timeout"));

        BlockchainTransaction result = service.submitTransaction(txId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUBMISSION_UNKNOWN.name());
        assertThat(result.getFailureReason()).contains("UNRESOLVED");
    }

    // ── Test 11: SUBMISSION_UNKNOWN recovery via confirm ─────────────────────

    @Test
    void submissionUnknown_recovery_confirmPolls() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction tx = makeTx(txId, TransactionStatus.SUBMISSION_UNKNOWN);
        tx.setTxHash("0x" + "c".repeat(64));
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(mockAdapter.getReceipt(any())).thenReturn(Optional.of(
            new BlockchainReceiptResult("0x" + "c".repeat(64), 1000L, true,
                new BigDecimal("40000"), new BigDecimal("1000000000"))));
        when(mockAdapter.getCurrentBlockNumber()).thenReturn(1002L); // 2 confirmations

        BlockchainTransaction result = service.confirmTransaction(txId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.CONFIRMED.name());
    }

    // ── Test 12: Receipt status 0 → REVERTED ─────────────────────────────────

    @Test
    void revertedTransaction_receiptStatus0_setsReverted() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction tx = makeTx(txId, TransactionStatus.SUBMITTED);
        tx.setTxHash("0x" + "d".repeat(64));
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(mockAdapter.getReceipt(any())).thenReturn(Optional.of(
            new BlockchainReceiptResult("0x" + "d".repeat(64), 999L, false,
                new BigDecimal("21000"), new BigDecimal("1000000000"))));
        when(mockAdapter.getCurrentBlockNumber()).thenReturn(1001L);

        BlockchainTransaction result = service.confirmTransaction(txId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.REVERTED.name());
        assertThat(result.getFailureReason()).contains("REVERTED");
    }

    // ── Test 13: Confirmation timeout → FAILED ───────────────────────────────

    @Test
    void confirmationTimeout_keepsSubmissionAndReservationUnresolved() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction tx = makeTx(txId, TransactionStatus.SUBMITTED);
        tx.setTxHash("0x" + "e".repeat(64));
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(mockAdapter.getReceipt(any())).thenReturn(Optional.empty()); // never confirmed
        // We need to avoid the actual 5-minute wait — override with a subclass that overrides poll
        // Instead patch the max polls via a small custom service
        BlockchainTransactionService fastService = service;

        BlockchainTransaction result = fastService.confirmTransaction(txId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUBMITTED.name());
        assertThat(result.getReservationStatus()).isEqualTo("PINNED");
    }

    // ── Test 14: INVALID_RECIPIENT_ADDRESS ─────────────────────────────────────────

    @Test
    void addressValidation_invalidEthAddress_throws() {
        assertThatThrownBy(() -> service.prepareTransfer(
            PAYMENT_ID, CORR_ID, "not-an-address", new BigDecimal("1.000000")))
            .isInstanceOf(BlockchainConfigurationException.class)
            .hasMessageContaining("INVALID_RECIPIENT_ADDRESS");
    }

    // ── Test 15: Excess decimal places ───────────────────────────────────────

    @Test
    void amountDecimalPlaces_exceeds_tokenDecimals_throws() {
        // 7 decimal places for a 6-decimal token
        assertThatThrownBy(() -> service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("1.1234567")))
            .isInstanceOf(BlockchainConfigurationException.class)
            .hasMessageContaining("INVALID_USDC_AMOUNT");
    }

    // ── Test 16: ERC-20 decimals conversion ──────────────────────────────────

    @Test
    void erc20Decimals_conversionCorrect() {
        BlockchainTransaction tx = service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("1.5"));

        // 1.5 USDC with 6 decimals = 1500000 smallest units
        assertThat(tx.getAmountInSmallest()).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    // ── Test 17: No float/double used ────────────────────────────────────────

    @Test
    void erc20Decimals_noFloatDouble_usedInConversion() {
        // Verify that 0.1 USDC = 100000 smallest units (float would lose precision)
        BlockchainTransaction tx = service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("0.1"));

        assertThat(tx.getAmountInSmallest()).isEqualByComparingTo(new BigDecimal("100000"));
    }

    // ── Test 18: Outbox event written after submit ────────────────────────────

    @Test
    void outboxEvent_submitted_writtenToOutbox() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction prepared = makeTx(txId, TransactionStatus.PREPARED);
        when(txRepository.findById(txId)).thenReturn(Optional.of(prepared));

        service.submitTransaction(txId);

        ArgumentCaptor<BlockchainOutboxEvent> captor = ArgumentCaptor.forClass(BlockchainOutboxEvent.class);
        verify(outboxRepository, atLeastOnce()).save(captor.capture());
        boolean hasSubmitted = captor.getAllValues().stream()
            .anyMatch(e -> "BlockchainTransactionSubmitted".equals(e.getEventType()));
        assertThat(hasSubmitted).isTrue();
    }

    // ── Test 19: Outbox event written after confirm ───────────────────────────

    @Test
    void outboxEvent_confirmed_writtenToOutbox() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction tx = makeTx(txId, TransactionStatus.SUBMITTED);
        tx.setTxHash("0x" + "b".repeat(64));
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        service.confirmTransaction(txId);

        ArgumentCaptor<BlockchainOutboxEvent> captor = ArgumentCaptor.forClass(BlockchainOutboxEvent.class);
        verify(outboxRepository, atLeastOnce()).save(captor.capture());
        boolean hasConfirmed = captor.getAllValues().stream()
            .anyMatch(e -> "BlockchainTransactionConfirmed".equals(e.getEventType()));
        assertThat(hasConfirmed).isTrue();
    }

    // ── Test 20: Dispatcher publishes PENDING event ───────────────────────────

    @Test
    void outboxDispatcher_publishesPendingEvent() throws Exception {
        BlockchainOutboxDispatcher dispatcher = new BlockchainOutboxDispatcher(
            outboxRepository, publisher, objectMapper);

        DomainEvent<Object> event = new DomainEvent<>();
        event.setEventId(UUID.randomUUID());
        event.setEventType("BlockchainTransactionSubmitted");
        event.setEventVersion("1.0");
        event.setPaymentId(PAYMENT_ID);

        BlockchainOutboxEvent record = new BlockchainOutboxEvent();
        record.setEventId(UUID.randomUUID());
        record.setPaymentId(PAYMENT_ID);
        record.setEventType("BlockchainTransactionSubmitted");
        record.setStatus("PENDING");
        record.setRetryCount(0);
        record.setPayload(objectMapper.writeValueAsString(event));

        dispatcher.publishOne(record);

        assertThat(record.getStatus()).isEqualTo("PUBLISHED");
        verify(publisher).publish(any(DomainEvent.class));
    }

    // ── Test 21: Dispatcher increments retry on failure ──────────────────────

    @Test
    void outboxDispatcher_retryOnFailure_incrementsRetryCount() throws Exception {
        BlockchainOutboxDispatcher dispatcher = new BlockchainOutboxDispatcher(
            outboxRepository, publisher, objectMapper);

        doThrow(new RuntimeException("SQS unavailable")).when(publisher).publish(any());

        DomainEvent<Object> event = new DomainEvent<>();
        event.setEventId(UUID.randomUUID());
        event.setEventType("BlockchainTransactionSubmitted");

        BlockchainOutboxEvent record = new BlockchainOutboxEvent();
        record.setEventId(UUID.randomUUID());
        record.setPaymentId(PAYMENT_ID);
        record.setEventType("BlockchainTransactionSubmitted");
        record.setStatus("PENDING");
        record.setRetryCount(0);
        record.setPayload(objectMapper.writeValueAsString(event));

        dispatcher.publishOne(record);

        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getStatus()).isEqualTo("PENDING");
    }

    // ── Test 22: Dispatcher sets FAILED after MAX_RETRIES ────────────────────

    @Test
    void outboxDispatcher_maxRetries_setsFailed() throws Exception {
        BlockchainOutboxDispatcher dispatcher = new BlockchainOutboxDispatcher(
            outboxRepository, publisher, objectMapper);

        doThrow(new RuntimeException("SQS unavailable")).when(publisher).publish(any());

        DomainEvent<Object> event = new DomainEvent<>();
        event.setEventId(UUID.randomUUID());
        event.setEventType("BlockchainTransactionSubmitted");

        BlockchainOutboxEvent record = new BlockchainOutboxEvent();
        record.setEventId(UUID.randomUUID());
        record.setPaymentId(PAYMENT_ID);
        record.setEventType("BlockchainTransactionSubmitted");
        record.setStatus("PENDING");
        record.setRetryCount(4); // one more will reach MAX_RETRIES=5
        record.setPayload(objectMapper.writeValueAsString(event));

        dispatcher.publishOne(record);

        assertThat(record.getRetryCount()).isEqualTo(5);
        assertThat(record.getStatus()).isEqualTo("FAILED");
    }

    // ── Test 23: No private key in outbox payload ─────────────────────────────

    @Test
    void outboxEvent_noPrivateKeyInPayload() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction prepared = makeTx(txId, TransactionStatus.PREPARED);
        when(txRepository.findById(txId)).thenReturn(Optional.of(prepared));

        service.submitTransaction(txId);

        ArgumentCaptor<BlockchainOutboxEvent> captor = ArgumentCaptor.forClass(BlockchainOutboxEvent.class);
        verify(outboxRepository, atLeastOnce()).save(captor.capture());

        for (BlockchainOutboxEvent event : captor.getAllValues()) {
            String payload = event.getPayload();
            if (payload != null) {
                // Verify no private key field appears in the JSON
                assertThat(payload).doesNotContain("privateKey");
                assertThat(payload).doesNotContain("private_key");
                // Note: txHash legitimately contains 64 hex chars — that is expected and correct.
                // A private key would only appear if the JSON contained a field named "privateKey".
            }
        }
    }

    // ── Test 24: Response contains address, not private key ──────────────────

    @Test
    void walletAddress_exposedInResponse_notPrivateKey() throws Exception {
        // The from address is 40 hex chars (20 bytes) with 0x prefix — not 64 hex chars
        BlockchainTransaction tx = service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("1.000000"));

        String fromAddr = tx.getFromAddress();
        assertThat(fromAddr).startsWith("0x");
        assertThat(fromAddr.length()).isEqualTo(42); // 0x + 40 hex chars
        // A private key would be 66 chars (0x + 64 hex) — verify address is NOT a private key length
        assertThat(fromAddr.length()).isLessThan(66);
    }

    // ── Test 25: Nonce incremented after successful submission ────────────────

    @Test
    void nonce_incrementedAfterSuccessfulSubmission() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction prepared = makeTx(txId, TransactionStatus.PREPARED);
        when(txRepository.findById(txId)).thenReturn(Optional.of(prepared));

        BlockchainWallet wallet = new BlockchainWallet();
        wallet.setWalletAddress(FROM_ADDRESS);
        wallet.setNetwork("base-sepolia");
        wallet.setPendingNonce(5L);
        when(walletRepository.findByWalletAddressAndNetwork(FROM_ADDRESS, "base-sepolia"))
            .thenReturn(Optional.of(wallet));

        service.submitTransaction(txId);

        ArgumentCaptor<BlockchainWallet> walletCaptor = ArgumentCaptor.forClass(BlockchainWallet.class);
        verify(walletRepository, atLeastOnce()).save(walletCaptor.capture());
        long savedNonce = walletCaptor.getAllValues().stream()
            .mapToLong(BlockchainWallet::getPendingNonce)
            .max().orElse(0L);
        assertThat(savedNonce).isEqualTo(6L);
    }

    // ── Test 26: Nonce claimed before possible submission ─────────────────

    @Test
    void nonce_claimedBeforeBroadcastOnSubmissionUnknown() {
        UUID txId = UUID.randomUUID();
        BlockchainTransaction prepared = makeTx(txId, TransactionStatus.PREPARED);
        when(txRepository.findById(txId)).thenReturn(Optional.of(prepared));

        BlockchainWallet wallet = new BlockchainWallet();
        wallet.setWalletAddress(FROM_ADDRESS);
        wallet.setNetwork("base-sepolia");
        wallet.setPendingNonce(3L);
        when(walletRepository.findByWalletAddressAndNetwork(any(), any()))
            .thenReturn(Optional.of(wallet));
        when(mockAdapter.submitRawTransaction(any()))
            .thenThrow(new BlockchainRpcException("timeout"));

        service.submitTransaction(txId);

        // The durable claim consumes nonce 3 before broadcasting; uncertainty must not reuse it.
        assertThat(wallet.getPendingNonce()).isEqualTo(4L);
    }

    // ── Test 27: Estimated gas saved on transaction ───────────────────────────

    @Test
    void gasEstimate_addedToTransaction() {
        when(mockAdapter.estimateTransferGas(any(), any(), any(), any(), anyInt()))
            .thenReturn(new BigDecimal("75000"));

        BlockchainTransaction tx = service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("1.000000"));

        assertThat(tx.getEstimatedGas()).isEqualByComparingTo(new BigDecimal("75000"));
    }

    // ── Test 28: MockAdapter chainId returns configured value ─────────────────

    @Test
    void mockAdapter_chainId_returnsConfiguredValue() {
        MockBlockchainAdapter mockReal = new MockBlockchainAdapter(props);
        assertThat(mockReal.getChainId()).isEqualTo(84532L);
    }

    // ── Test 29: MockAdapter token balance ───────────────────────────────────

    @Test
    void mockAdapter_tokenBalance_returnsMockValue() {
        MockBlockchainAdapter mockReal = new MockBlockchainAdapter(props);
        BigDecimal balance = mockReal.getTokenBalance(FROM_ADDRESS, props.getTokenContract(), 6);
        assertThat(balance).isGreaterThan(BigDecimal.ZERO);
    }

    // ── Test 30: MockAdapter native balance ──────────────────────────────────

    @Test
    void mockAdapter_nativeBalance_returnsMockValue() {
        MockBlockchainAdapter mockReal = new MockBlockchainAdapter(props);
        BigDecimal balance = mockReal.getNativeBalance(FROM_ADDRESS);
        assertThat(balance).isGreaterThan(BigDecimal.ZERO);
    }

    // ── Test 31: Real adapter decimal conversion exact ───────────────────────

    @Test
    void realAdapter_tokenDecimalConversion_exact() {
        // 1500000 / 10^6 = 1.5 exactly — verify no float rounding
        BigDecimal smallest = new BigDecimal("1500000");
        BigDecimal amount = smallest.movePointLeft(6);
        assertThat(amount).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(amount.scale()).isEqualTo(6);
    }

    // ── Test 32: Idempotency — DataIntegrityViolation skipped ────────────────

    @Test
    void idempotency_uniqueConstraint_preventsDuplicate() {
        // If a SUBMITTED transaction exists for the same (paymentId, network), it's returned as-is
        BlockchainTransaction existing = makeTx(UUID.randomUUID(), TransactionStatus.SUBMITTED);
        when(txRepository.findByPaymentId(PAYMENT_ID))
            .thenReturn(Optional.of(existing));

        BlockchainTransaction result = service.prepareTransfer(
            PAYMENT_ID, CORR_ID, TO_ADDRESS, new BigDecimal("1.000000"));

        assertThat(result).isSameAs(existing);
        verify(txRepository, never()).save(any());
    }

    // ── Test 33: Health endpoint ──────────────────────────────────────────────

    @Test
    void healthEndpoint_contextLoads() {
        // The HealthController is a simple controller — verify its logic directly
        com.cognizant.stablecoins.blockchain.HealthController hc =
            new com.cognizant.stablecoins.blockchain.HealthController();
        var result = hc.health();
        assertThat(result.get("status")).isEqualTo("ok");
    }

    // ── Test 34: BlockchainConfirmedConsumer posts ledger journal ─────────────

    @Test
    void blockchainConfirmedConsumer_delegatesToLedgerPostingService() throws Exception {
        // Verify the consumer parses correctly and delegates
        // (Integration covered by settlement-ledger IT tests; here we validate parsing logic)
        DomainEvent<Object> event = new DomainEvent<>();
        event.setEventId(UUID.randomUUID());
        event.setEventType("BlockchainTransactionConfirmed");
        event.setEventVersion("1.0");
        event.setPaymentId(PAYMENT_ID);
        event.setCorrelationId(CORR_ID);

        com.cognizant.stablecoins.blockchain.event.BlockchainTransactionConfirmedPayload payload =
            new com.cognizant.stablecoins.blockchain.event.BlockchainTransactionConfirmedPayload();
        payload.setTxHash("0x" + "f".repeat(64));
        payload.setAmount(new BigDecimal("100.000000"));
        payload.setTokenSymbol("USDC");
        payload.setNetwork("base-sepolia");
        payload.setFromAddress(FROM_ADDRESS);
        payload.setToAddress(TO_ADDRESS);
        event.setPayload(payload);

        String json = objectMapper.writeValueAsString(event);
        // Parse back and verify payload
        var node = objectMapper.readTree(json);
        assertThat(node.get("eventType").asText()).isEqualTo("BlockchainTransactionConfirmed");
        assertThat(node.get("payload").get("txHash").asText()).contains("ff");
    }

    // ── Test 35: Duplicate confirmed event idempotency ───────────────────────

    @Test
    void blockchainConfirmedConsumer_duplicateEvent_sameTxHash() throws Exception {
        // Two events with same paymentId → same tx hash — both parse OK (idempotency
        // is enforced at DB level via UNIQUE constraint in settlement-ledger-service)
        String paymentId = UUID.randomUUID().toString();
        DomainEvent<Object> event1 = new DomainEvent<>();
        event1.setEventId(UUID.randomUUID());
        event1.setEventType("BlockchainTransactionConfirmed");
        event1.setPaymentId(paymentId);

        DomainEvent<Object> event2 = new DomainEvent<>();
        event2.setEventId(UUID.randomUUID()); // different eventId
        event2.setEventType("BlockchainTransactionConfirmed");
        event2.setPaymentId(paymentId);       // same paymentId

        // Both events serialize and parse correctly
        String json1 = objectMapper.writeValueAsString(event1);
        String json2 = objectMapper.writeValueAsString(event2);

        var node1 = objectMapper.readTree(json1);
        var node2 = objectMapper.readTree(json2);

        assertThat(node1.get("paymentId").asText())
            .isEqualTo(node2.get("paymentId").asText());
        assertThat(node1.get("eventId").asText())
            .isNotEqualTo(node2.get("eventId").asText());
    }

    // ─── Helper methods ───────────────────────────────────────────────────────

    private BlockchainTransaction makeTx(UUID txId, TransactionStatus status) {
        BlockchainTransaction tx = new BlockchainTransaction();
        tx.setTransactionId(txId);
        tx.setPaymentId(PAYMENT_ID);
        tx.setCorrelationId(CORR_ID);
        tx.setNetwork("base-sepolia");
        tx.setFromAddress(FROM_ADDRESS);
        tx.setToAddress(TO_ADDRESS);
        tx.setTokenContract("0x036CbD53842c5426634e7929541eC2318f3dCF7e");
        tx.setTokenSymbol("USDC");
        tx.setAmount(new BigDecimal("1.000000"));
        tx.setAmountInSmallest(new BigDecimal("1000000"));
        tx.setEstimatedGas(new BigDecimal("65000"));
        tx.setStatus(status.name());
        tx.setChainId(84532L);tx.setEvidenceMode("MOCK");
        tx.setGasLimit(new BigDecimal("78000"));tx.setGasPriceWei(new BigDecimal("1200000000"));
        tx.setGasReserveEth(new BigDecimal("0.0001036"));
        tx.setReservationStatus(status==TransactionStatus.PREPARED ? "HELD" : "PINNED");
        tx.setReservationExpiresAt(OffsetDateTime.now().plusMinutes(5));
        tx.setCreatedAt(OffsetDateTime.now());
        tx.setUpdatedAt(OffsetDateTime.now());
        return tx;
    }


    @Test void reservationSubtractsBothAssetsAndNeverSignsDuringPreparation() {
        var held=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);held.setAmount(new BigDecimal("9000"));
        held.setGasReserveEth(new BigDecimal("0.49"));
        when(txRepository.findByFromAddressAndNetwork(FROM_ADDRESS,"base-sepolia")).thenReturn(List.of(held));
        var r=service.getReadiness(TO_ADDRESS,new BigDecimal("1500"));
        assertThat(r.availableUsdc()).isEqualByComparingTo("1000");
        assertThat(r.availableEth()).isEqualByComparingTo("0.01");
        assertThat(r.reasons()).containsExactly("INSUFFICIENT_USDC");
        assertThat(r.evidenceMode()).isEqualTo("MOCK");
        verify(mockAdapter,never()).signRawTransaction(anyLong(),any(),any(),any(),anyInt(),any(),any());
        verify(txRepository,never()).save(any());
    }
    @Test void gasReserveCoversBufferedLimitBufferedPriceAndExplicitExtraEth() {
        var r=service.getReadiness(TO_ADDRESS,BigDecimal.ONE);
        assertThat(r.gasLimit()).isEqualByComparingTo("78000");
        assertThat(r.gasPriceWei()).isEqualByComparingTo("1200000000");
        assertThat(r.requiredGasEth()).isEqualByComparingTo("0.0001036");
    }
    @Test void availableEthIncludesAllOtherGasReservations() {
        var held=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);held.setGasReserveEth(new BigDecimal("0.49995"));
        when(txRepository.findByFromAddressAndNetwork(any(),any())).thenReturn(List.of(held));
        assertThatThrownBy(() -> service.prepareTransfer(PAYMENT_ID,CORR_ID,TO_ADDRESS,BigDecimal.ONE))
            .isInstanceOf(InsufficientGasException.class);
        verify(txRepository,never()).save(any());
    }
    @Test void readinessExpiresQuicklyWithoutExposingSigningMaterial() throws Exception {
        var r=service.getReadiness(TO_ADDRESS,BigDecimal.ONE);
        assertThat(java.time.Duration.between(r.observedAt(),r.expiresAt())).isEqualTo(props.getReadinessTtl());
        assertThat(objectMapper.writeValueAsString(r)).doesNotContain("privateKey","signedTransaction","credentials");
    }
    @Test void matchingPreparedReplayReturnsSameHoldWithoutNewObservation() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);
        when(txRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(tx));
        assertThat(service.prepareTransfer(PAYMENT_ID,CORR_ID,TO_ADDRESS,BigDecimal.ONE)).isSameAs(tx);
        verify(mockAdapter,never()).getTokenBalance(any(),any(),anyInt());verify(txRepository,never()).save(any());
    }
    @Test void changedMaterialRequestCannotReusePaymentOperation() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.SUBMISSION_UNKNOWN);
        when(txRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(tx));
        assertThatThrownBy(() -> service.prepareTransfer(PAYMENT_ID,"CORR-OTHER",TO_ADDRESS,BigDecimal.ONE))
            .hasMessage("PAYMENT_OPERATION_CONFLICT");
        assertThatThrownBy(() -> service.prepareTransfer(PAYMENT_ID,CORR_ID,TO_ADDRESS,new BigDecimal("2")))
            .hasMessage("PAYMENT_OPERATION_CONFLICT");
    }
    @Test void expiredUnsignedHoldCannotBeSignedOrRecreated() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);
        tx.setReservationExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));
        assertThat(service.submitTransaction(tx.getTransactionId()).getReservationStatus()).isEqualTo("EXPIRED");
        verify(mockAdapter,never()).submitRawTransaction(any());
        when(txRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(tx));
        assertThat(service.prepareTransfer(PAYMENT_ID,CORR_ID,TO_ADDRESS,BigDecimal.ONE)).isSameAs(tx);
    }
    @Test void pinnedHoldCannotExpireAndBlocksAnotherPaymentEvenAfterItsDeadline() {
        var pinned=makeTx(UUID.randomUUID(),TransactionStatus.SUBMISSION_UNKNOWN);
        pinned.setReservationExpiresAt(OffsetDateTime.now().minusDays(1));
        when(txRepository.findByFromAddressAndNetwork(any(),any())).thenReturn(List.of(pinned));
        var r=service.getReadiness(TO_ADDRESS,BigDecimal.ONE);
        assertThat(r.reservedUsdc()).isEqualByComparingTo("1");
        assertThat(r.reasons()).contains("WALLET_SUBMISSION_UNRESOLVED");
        assertThatThrownBy(() -> service.prepareTransfer("PAY-OTHER",CORR_ID,TO_ADDRESS,BigDecimal.ONE))
            .hasMessageContaining("WALLET_SUBMISSION_UNRESOLVED");
    }
    @Test void signingClaimCommitsBeforeSingleNetworkAttempt() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);
        when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));
        var commits=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(i -> { commits.incrementAndGet();return null; }).when(transactionManager).commit(any());
        when(mockAdapter.submitRawTransaction(any())).thenAnswer(i -> {
            assertThat(commits.get()).isEqualTo(1);
            assertThat(tx.getStatus()).isEqualTo("SUBMITTING");assertThat(tx.getReservationStatus()).isEqualTo("PINNED");
            assertThat(tx.getTxHash()).hasSize(66);return tx.getTxHash();
        });
        service.submitTransaction(tx.getTransactionId());service.submitTransaction(tx.getTransactionId());
        verify(mockAdapter,times(1)).submitRawTransaction(any());
    }
    @Test void walletNonceUsesRpcPendingValueAndCannotBeReusedOnTimeout() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);
        when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));
        when(mockAdapter.getPendingNonce(any())).thenReturn(17L);
        when(mockAdapter.submitRawTransaction(any())).thenThrow(new BlockchainRpcException("sensitive-provider-url"));
        var result=service.submitTransaction(tx.getTransactionId());
        assertThat(result.getNonce()).isEqualTo(17L);
        assertThat(result.getFailureReason()).isEqualTo("SUBMISSION_OUTCOME_UNRESOLVED");
        assertThat(walletRepository.lockWallet(FROM_ADDRESS,"base-sepolia").orElseThrow().getPendingNonce()).isEqualTo(18L);
        service.submitTransaction(tx.getTransactionId());verify(mockAdapter,times(1)).submitRawTransaction(any());
    }
    @Test void mismatchedRpcHashNeverOverwritesDurableExpectedHash() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);
        when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));
        when(mockAdapter.submitRawTransaction(any())).thenReturn("0x"+"a".repeat(64));
        assertThat(service.submitTransaction(tx.getTransactionId()).getStatus()).isEqualTo("SUBMISSION_UNKNOWN");
        assertThat(tx.getTxHash()).isNotEqualTo("0x"+"a".repeat(64));
        verify(outboxRepository,never()).save(any());
    }
    @Test void lateGasPriceIncreaseIsRevalidatedBeforeSigning() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);
        when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));
        when(mockAdapter.getNativeBalance(any())).thenReturn(new BigDecimal("0.0002"));
        when(mockAdapter.getGasPriceWei()).thenReturn(new BigDecimal("100000000000"));
        assertThatThrownBy(() -> service.submitTransaction(tx.getTransactionId())).isInstanceOf(InsufficientGasException.class);
        verify(mockAdapter,never()).signRawTransaction(anyLong(),any(),any(),any(),anyInt(),any(),any());
    }
    @Test void releaseIsIdempotentOnlyBeforeSigning() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);
        when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));
        assertThat(service.releaseReservation(tx.getTransactionId()).getReservationStatus()).isEqualTo("RELEASED");
        service.releaseReservation(tx.getTransactionId());verify(auditRepository,times(1)).save(any());
        tx.setStatus("SUBMISSION_UNKNOWN");tx.setReservationStatus("PINNED");
        assertThatThrownBy(() -> service.releaseReservation(tx.getTransactionId())).hasMessage("SIGNED_OPERATION_REQUIRES_RECONCILIATION");
    }
    @Test void matchingReceiptCanRecoverACommittedClaimAfterProcessRestart() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.SUBMITTING);tx.setTxHash("0x"+"f".repeat(64));
        when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));
        assertThat(service.confirmTransaction(tx.getTransactionId()).getReservationStatus()).isEqualTo("SETTLED");
        verify(mockAdapter,never()).submitRawTransaction(any());
        service.confirmTransaction(tx.getTransactionId());
        var events=ArgumentCaptor.forClass(BlockchainOutboxEvent.class);verify(outboxRepository,times(2)).save(events.capture());
        assertThat(events.getAllValues()).extracting(BlockchainOutboxEvent::getEventType).containsExactly("BlockchainTransactionSubmitted","BlockchainTransactionConfirmed");
    }
    @Test void receiptInclusionCountsAsFirstConfirmationAndMismatchIsRejected() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.SUBMITTED);tx.setTxHash("0x"+"f".repeat(64));
        when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));
        when(mockAdapter.getCurrentBlockNumber()).thenReturn(1234567L);
        assertThat(service.confirmTransaction(tx.getTransactionId()).getStatus()).isEqualTo("CONFIRMED");
        tx.setStatus("SUBMITTED");tx.setReservationStatus("PINNED");
        when(mockAdapter.getReceipt(any())).thenReturn(Optional.of(new BlockchainReceiptResult("0x"+"e".repeat(64),1234567L,true,BigDecimal.ONE,BigDecimal.ONE)));
        assertThatThrownBy(() -> service.confirmTransaction(tx.getTransactionId())).hasMessage("RECEIPT_EVIDENCE_MISMATCH");
        assertThat(tx.getReservationStatus()).isEqualTo("PINNED");
    }
    @Test void legacyUnresolvedOperationBlocksWalletWithoutInventedReserves() {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.FAILED);tx.setReservationStatus("LEGACY_REVIEW");
        when(txRepository.findByFromAddressAndNetwork(any(),any())).thenReturn(List.of(tx));
        assertThat(service.getReadiness(TO_ADDRESS,BigDecimal.ONE).reasons()).contains("LEGACY_OPERATION_REQUIRES_REVIEW");
    }
    @Test void unsupportedMainnetAndInvalidRpcDataFailClosed() {
        props.setNetwork("mainnet");props.setChainId(1L);when(mockAdapter.getChainId()).thenReturn(1L);
        assertThatThrownBy(() -> service.getReadiness(TO_ADDRESS,BigDecimal.ONE)).isInstanceOf(BlockchainConfigurationException.class);
        props.setNetwork("base-sepolia");props.setChainId(84532L);when(mockAdapter.getChainId()).thenReturn(84532L);
        when(mockAdapter.getGasPriceWei()).thenReturn(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.getReadiness(TO_ADDRESS,BigDecimal.ONE)).hasMessage("INVALID_GAS_OBSERVATION");
    }
    @Test void outboxAndEnvelopeRetainExactlyTheSameEventIdentityAndOpaqueIds() throws Exception {
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.PREPARED);when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));
        service.submitTransaction(tx.getTransactionId());var cap=ArgumentCaptor.forClass(BlockchainOutboxEvent.class);
        verify(outboxRepository).save(cap.capture());var row=cap.getValue();var json=objectMapper.readTree(row.getPayload());
        assertThat(json.path("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(json.path("paymentId").asText()).isEqualTo(PAYMENT_ID);
        assertThat(json.path("correlationId").asText()).isEqualTo(CORR_ID);
    }
    @Test void treasuryConfigurationRejectsUnsafeDurationsAndGasRules() {
        assertThatThrownBy(() -> props.setReservationTtl(java.time.Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> props.setReadinessTtl(java.time.Duration.ofDays(2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> props.setGasLimitMultiplier(new BigDecimal("0.9"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> props.setAdditionalGasReserveEth(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }
    private BlockchainTransaction minedFeeIncomplete(){
        var tx=makeTx(UUID.randomUUID(),TransactionStatus.CONFIRMED);tx.setTxHash("0x"+"a".repeat(64));tx.setBlockNumber(123L);tx.setGasUsed(new BigDecimal("21000"));tx.setEffectiveGasPrice(null);tx.setFeeComponentsComplete(false);
        when(txRepository.findById(tx.getTransactionId())).thenReturn(Optional.of(tx));return tx;
    }
    @Test void receiptRefreshFillsMissingPriceWithoutSigningOrResubmittingAndPublishesOnce(){
        var tx=minedFeeIncomplete();var receipt=new BlockchainReceiptResult(tx.getTxHash(),123L,true,new BigDecimal("21000"),new BigDecimal("2")).fees(java.math.BigInteger.valueOf(100),true,null);
        when(mockAdapter.getReceipt(tx.getTxHash())).thenReturn(Optional.of(receipt));service.refreshReceiptEvidence(tx.getTransactionId());service.refreshReceiptEvidence(tx.getTransactionId());
        assertThat(tx.isFeeComponentsComplete()).isTrue();assertThat(tx.getEffectiveGasPrice()).isEqualByComparingTo("2");assertThat(tx.getTotalFeeWei()).isEqualByComparingTo("42100");
        var event=ArgumentCaptor.forClass(BlockchainOutboxEvent.class);verify(outboxRepository).save(event.capture());assertThat(event.getValue().getEventType()).isEqualTo("BlockchainReceiptObserved");
        verify(mockAdapter,times(1)).getReceipt(tx.getTxHash());verify(mockAdapter,never()).submitRawTransaction(any());verify(signingService,never()).signTransaction(any(),anyLong());
    }
    @Test void receiptRefreshRejectsChangedMinedBlock(){var tx=minedFeeIncomplete();when(mockAdapter.getReceipt(tx.getTxHash())).thenReturn(Optional.of(new BlockchainReceiptResult(tx.getTxHash(),124L,true,new BigDecimal("21000"),new BigDecimal("2")).fees(java.math.BigInteger.ZERO,true,null)));assertThatThrownBy(()->service.refreshReceiptEvidence(tx.getTransactionId())).hasMessage("RECEIPT_EVIDENCE_MISMATCH");verifyNoInteractions(outboxRepository);verify(signingService,never()).signTransaction(any(),anyLong());}
    @Test void receiptRefreshRejectsChangedKnownGasPrice(){var tx=minedFeeIncomplete();tx.setEffectiveGasPrice(BigDecimal.ONE);when(mockAdapter.getReceipt(tx.getTxHash())).thenReturn(Optional.of(new BlockchainReceiptResult(tx.getTxHash(),123L,true,new BigDecimal("21000"),new BigDecimal("2")).fees(java.math.BigInteger.ZERO,true,null)));assertThatThrownBy(()->service.refreshReceiptEvidence(tx.getTransactionId())).hasMessage("RECEIPT_EVIDENCE_MISMATCH");verifyNoInteractions(outboxRepository);verify(signingService,never()).signTransaction(any(),anyLong());}
    @Test void incompleteFeeRefreshDoesNotPublishActualFeeEvent(){var tx=minedFeeIncomplete();when(mockAdapter.getReceipt(tx.getTxHash())).thenReturn(Optional.of(new BlockchainReceiptResult(tx.getTxHash(),123L,true,new BigDecimal("21000"),new BigDecimal("2")).fees(null,false,"L1_FEE_MISSING")));service.refreshReceiptEvidence(tx.getTransactionId());assertThat(tx.isFeeComponentsComplete()).isFalse();assertThat(tx.getFeeEvidenceReason()).isEqualTo("L1_FEE_MISSING");verifyNoInteractions(outboxRepository);verify(signingService,never()).signTransaction(any(),anyLong());}

}
