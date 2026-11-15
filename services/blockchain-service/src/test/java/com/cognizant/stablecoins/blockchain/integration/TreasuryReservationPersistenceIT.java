package com.cognizant.stablecoins.blockchain.integration;
import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import com.cognizant.stablecoins.blockchain.model.*;
import com.cognizant.stablecoins.blockchain.repository.*;
import com.cognizant.stablecoins.blockchain.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Real PostgreSQL migrations, locking and commit boundaries; never substituted by an in-memory database. */
@DataJpaTest @ActiveProfiles("test") @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({BlockchainTransactionService.class,BlockchainProperties.class,TreasuryReservationPersistenceIT.Config.class})
class TreasuryReservationPersistenceIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    }
    @TestConfiguration static class Config {
        @Bean ObjectMapper mapper(){return new ObjectMapper().registerModule(new JavaTimeModule());}
    }
    @Autowired BlockchainTransactionService service;
    @Autowired BlockchainTransactionRepository transactions;
    @Autowired BlockchainWalletRepository wallets;
    @Autowired BlockchainExecutionAuditRepository audit;
    @SpyBean BlockchainOutboxEventRepository outbox;
    @Autowired PlatformTransactionManager tm;
    @MockBean BlockchainAdapter adapter;
    @MockBean WalletSigningService signer;
    static final String FROM="0x"+"1".repeat(40),TO="0x"+"2".repeat(40);
    @BeforeEach void setup() {
        new TransactionTemplate(tm).execute(s -> {audit.deleteAll();outbox.deleteAll();transactions.deleteAll();wallets.deleteAll();return null;});
        when(signer.getWalletAddress()).thenReturn(FROM);when(adapter.getEvidenceMode()).thenReturn("MOCK");
        when(adapter.getChainId()).thenReturn(84532L);when(adapter.getGasPriceWei()).thenReturn(new BigDecimal("1000000000"));
        when(adapter.estimateTransferGas(any(),any(),any(),any(),anyInt())).thenReturn(new BigDecimal("65000"));
        when(adapter.getNativeBalance(any())).thenReturn(new BigDecimal("1"));
        when(adapter.getTokenBalance(any(),any(),anyInt())).thenReturn(new BigDecimal("10000"));
        when(adapter.signRawTransaction(anyLong(),any(),any(),any(),anyInt(),any(),any())).thenReturn(new byte[]{1,2,3});
    }
    @Test void concurrentUsdTransfersCannotOverAllocateTreasuryAndGas() throws Exception {
        var start=new CountDownLatch(1);var ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> attempt("PAY-A",ready,start));var second=pool.submit(() -> attempt("PAY-B",ready,start));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("HELD","INSUFFICIENT");
        }
        assertThat(transactions.count()).isEqualTo(1);assertThat(wallets.count()).isEqualTo(1);
        assertThat(audit.count()).isEqualTo(1);
    }
    private String attempt(String id,CountDownLatch ready,CountDownLatch start) throws Exception {
        ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new AssertionError("start timeout");
        try{return service.prepareTransfer(id,"CORR-1",TO,new BigDecimal("7000")).getReservationStatus();}
        catch(InsufficientTokenBalanceException expected){return "INSUFFICIENT";}
    }
    @Test void concurrentReplayCreatesOneOperationAndOneAudit() throws Exception {
        var start=new CountDownLatch(1);var ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<UUID> task=() -> {ready.countDown();start.await(5,TimeUnit.SECONDS);return service.prepareTransfer("PAY-SAME","CORR-SAME",TO,BigDecimal.ONE).getTransactionId();};
            var a=pool.submit(task);var b=pool.submit(task);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
        }
        assertThat(transactions.count()).isEqualTo(1);assertThat(audit.count()).isEqualTo(1);
    }
    @Test void broadcastingSeesCommittedClaimAndRpcTimeoutKeepsItRecoverable() {
        var tx=service.prepareTransfer("PAY-CLAIM","CORR-CLAIM",TO,BigDecimal.ONE);
        when(adapter.submitRawTransaction(any())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var persisted=transactions.findById(tx.getTransactionId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo("SUBMITTING");assertThat(persisted.getReservationStatus()).isEqualTo("PINNED");
            assertThat(persisted.getTxHash()).hasSize(66);assertThat(persisted.getNonce()).isZero();
            throw new BlockchainRpcException("timeout");
        });
        service.submitTransaction(tx.getTransactionId());service.submitTransaction(tx.getTransactionId());
        var stored=transactions.findById(tx.getTransactionId()).orElseThrow();assertThat(stored.getStatus()).isEqualTo("SUBMISSION_UNKNOWN");
        assertThat(stored.getReservationStatus()).isEqualTo("PINNED");verify(adapter,times(1)).submitRawTransaction(any());
    }
    @Test void outboxFailureAfterBroadcastCannotEraseCommittedHashOrPermitSecondSend() {
        var tx=service.prepareTransfer("PAY-OUTBOX","CORR-OUTBOX",TO,BigDecimal.ONE);
        when(adapter.submitRawTransaction(any())).thenAnswer(i -> org.web3j.utils.Numeric.toHexString(org.web3j.crypto.Hash.sha3((byte[])i.getArgument(0))));
        doThrow(new IllegalStateException("injected outbox failure")).when(outbox).save(any());
        assertThatThrownBy(() -> service.submitTransaction(tx.getTransactionId())).hasMessage("injected outbox failure");
        var stored=transactions.findById(tx.getTransactionId()).orElseThrow();assertThat(stored.getStatus()).isEqualTo("SUBMITTING");
        assertThat(stored.getReservationStatus()).isEqualTo("PINNED");assertThat(stored.getTxHash()).hasSize(66);
        service.submitTransaction(tx.getTransactionId());verify(adapter,times(1)).submitRawTransaction(any());
    }
    @Test void unsignedExpiryIsPersistedButSubmittedReservationSurvivesExpiry() {
        var tx=service.prepareTransfer("PAY-EXPIRE","CORR-EXPIRE",TO,BigDecimal.ONE);
        new TransactionTemplate(tm).execute(s -> {var row=transactions.findById(tx.getTransactionId()).orElseThrow();row.setReservationExpiresAt(OffsetDateTime.now().minusSeconds(1));return null;});
        assertThat(service.submitTransaction(tx.getTransactionId()).getReservationStatus()).isEqualTo("EXPIRED");
        assertThat(transactions.findById(tx.getTransactionId()).orElseThrow().getStatus()).isEqualTo("FAILED");verify(adapter,never()).submitRawTransaction(any());
    }
}
