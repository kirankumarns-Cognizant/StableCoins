package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.ResultSet;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.payment.settlement.BindingFixtures.*;

class SettlementBindingStoreTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final SettlementBindingStore store = new SettlementBindingStore(jdbc);
    final Map<UUID, Object[]> rows = new HashMap<>();
    final SettlementBinding.Instruction instruction = instruction();

    @BeforeEach @SuppressWarnings({"unchecked", "rawtypes"})
    void setup() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "correlation_id", instruction.correlationId(), "customer_id", instruction.customerId())));
        when(jdbc.update(startsWith("insert into payment_settlement_binding"), any(Object[].class)))
                .thenAnswer(call -> {
                    Object[] args = (Object[]) call.getRawArguments()[1];
                    return rows.putIfAbsent((UUID) args[0], args.clone()) == null ? 1 : 0;
                });
        when(jdbc.query(startsWith("select document::text"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(call -> {
                    Object[] args = (Object[]) call.getRawArguments()[2];
                    Object[] row = rows.get(args[0]);
                    if (row == null || !row[1].equals(args[1])) return List.of();
                    var rs = mock(ResultSet.class);
                    when(rs.getString(1)).thenReturn((String) row[9]);
                    when(rs.getString(2)).thenReturn((String) row[10]);
                    return List.of(((RowMapper) call.getArgument(1)).mapRow(rs, 0));
                });
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); }

    @Test void exactInstructionReplayIsIdempotent() {
        store.captureInstruction(instruction); store.captureInstruction(instruction);
        assertThat(rows).hasSize(1);
    }
    @Test void changedInstructionUnderSameKeyCannotOverwriteOriginal() {
        store.captureInstruction(instruction);
        assertThatThrownBy(() -> store.captureInstruction(changedRecipient(instruction)))
                .hasMessage("SETTLEMENT_BINDING_CONFLICT");
        assertThat(SettlementBindingJson.read((String) rows.get(instruction.intentId())[9],
                SettlementBinding.Instruction.class)).isEqualTo(instruction);
    }
    @Test void reviewRequiresExactCapturedInstruction() {
        store.captureInstruction(instruction);
        assertThatThrownBy(() -> store.captureReview(review(changedRecipient(instruction))))
                .hasMessage("SETTLEMENT_PARENT_BINDING_CHANGED");
        assertThat(rows).hasSize(1);
    }
    @Test void missingInstructionCannotBeInferredFromCurrentDefaults() {
        assertThatThrownBy(() -> store.captureReview(review(instruction)))
                .hasMessage("SETTLEMENT_PARENT_BINDING_CHANGED");
        assertThat(rows).isEmpty();
    }
    @Test void executionRetainsTheExactReviewAndReplayDoesNotCreateAnotherBinding() {
        var review = review(instruction); var execution = new SettlementBinding.Execution(UUID.randomUUID(), review);
        store.captureInstruction(instruction); store.captureReview(review);
        store.captureExecution(execution); store.captureExecution(execution);
        assertThat(rows).hasSize(3);
        assertThat(store.execution(execution.executionId())).contains(execution);
    }
    @Test void differentReviewCannotReplaceExecutionMaterial() {
        var review = review(instruction); var other = review(instruction);
        var id = UUID.randomUUID();
        store.captureInstruction(instruction); store.captureReview(review); store.captureReview(other);
        store.captureExecution(new SettlementBinding.Execution(id, review));
        assertThatThrownBy(() -> store.captureExecution(new SettlementBinding.Execution(id, other)))
                .hasMessage("SETTLEMENT_BINDING_CONFLICT");
    }
    @Test void persistedDocumentHashMismatchFailsClosed() {
        store.captureInstruction(instruction);
        rows.get(instruction.intentId())[10] = "0".repeat(64);
        assertThatThrownBy(() -> store.captureReview(review(instruction)))
                .hasMessage("SETTLEMENT_BINDING_INTEGRITY_FAILED");
    }
    @Test void writeRequiresThePaymentOwnersTransaction() {
        TransactionSynchronizationManager.clear();
        assertThatThrownBy(() -> store.captureInstruction(instruction))
                .hasMessage("PAYMENT_OWNER_TRANSACTION_REQUIRED");
        verifyNoInteractions(jdbc);
    }
    @Test void customerOrCorrelationMismatchCannotCreateBindings() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "correlation_id", instruction.correlationId(), "customer_id", UUID.randomUUID())));
        assertThatThrownBy(() -> store.captureInstruction(instruction))
                .hasMessage("SETTLEMENT_PAYMENT_SCOPE_MISMATCH");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
