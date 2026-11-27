package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.SettlementBinding;
import com.cognizant.stablecoins.flow.SettlementBindingJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment-owner DAO for ADR-016 snapshots. Deliberately not registered as a workflow
 * or exposed by an endpoint. The owner must authorize the customer, confirm the
 * intent, and perform existing quote/two-approver checks in the SAME transaction.
 * This store cannot approve, reserve, sign, submit or mutate accounting records.
 */
public final class SettlementBindingStore {
    private final JdbcTemplate jdbc;
    public SettlementBindingStore(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

    public void captureInstruction(SettlementBinding.Instruction instruction) {
        lockPayment(instruction);
        append(instruction.intentId(), "INSTRUCTION", instruction, null, null, null, null, null,
                instruction);
    }

    public void captureReview(SettlementBinding.Review review) {
        var instruction = review.instruction();
        lockPayment(instruction);
        requireParent(instruction.intentId(), "INSTRUCTION", instruction, SettlementBinding.Instruction.class);
        append(review.recommendationId(), "REVIEW", review, instruction.intentId(), "INSTRUCTION",
                review.recommendationId(), review.preparationId(), null, instruction);
    }

    public void captureExecution(SettlementBinding.Execution execution) {
        var review = execution.review();
        var instruction = execution.instruction();
        lockPayment(instruction);
        requireParent(review.recommendationId(), "REVIEW", review, SettlementBinding.Review.class);
        append(execution.executionId(), "EXECUTION", execution, review.recommendationId(), "REVIEW",
                review.recommendationId(), review.preparationId(), execution.executionId(), instruction);
    }

    /** Stored material alone is never evidence that an execution is approved. */
    public Optional<SettlementBinding.Execution> execution(UUID executionId) {
        return load(executionId, "EXECUTION", SettlementBinding.Execution.class);
    }

    private void lockPayment(SettlementBinding.Instruction instruction) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("PAYMENT_OWNER_TRANSACTION_REQUIRED");
        var rows = jdbc.queryForList(
                "select p.correlation_id,a.customer_id from payment p join account a on a.account_id=p.sender_account_id where p.payment_id=? for update of p",
                instruction.paymentId());
        if (rows.size() != 1 ||
                !instruction.correlationId().equals(rows.getFirst().get("correlation_id")) ||
                !instruction.customerId().equals(rows.getFirst().get("customer_id")))
            throw new IllegalStateException("SETTLEMENT_PAYMENT_SCOPE_MISMATCH");
    }

    private <T> void requireParent(UUID id, String phase, T expected, Class<T> type) {
        if (load(id, phase, type).filter(expected::equals).isEmpty())
            throw new IllegalStateException("SETTLEMENT_PARENT_BINDING_CHANGED");
    }

    private void append(UUID id, String phase, Object value, UUID parentId, String parentPhase,
            UUID recommendationId, UUID preparationId, UUID executionId,
            SettlementBinding.Instruction instruction) {
        String document = SettlementBindingJson.write(value);
        String fingerprint = SettlementBindingJson.fingerprint(value);
        jdbc.update("insert into payment_settlement_binding(binding_id,phase,payment_id,correlation_id,parent_id,parent_phase,recommendation_id,preparation_id,execution_id,document,fingerprint) values (?,?,?,?,?,?,?,?,?,cast(? as jsonb),?) on conflict(binding_id) do nothing",
                id, phase, instruction.paymentId(), instruction.correlationId(), parentId, parentPhase,
                recommendationId, preparationId, executionId, document, fingerprint);
        // ON CONFLICT only makes EXACT replay idempotent. A changed key/material never overwrites.
        var stored = load(id, phase, value.getClass());
        if (stored.isEmpty() || !value.equals(stored.get()))
            throw new IllegalStateException("SETTLEMENT_BINDING_CONFLICT");
    }

    private <T> Optional<T> load(UUID id, String phase, Class<T> type) {
        List<T> values = jdbc.query(
                "select document::text,fingerprint from payment_settlement_binding where binding_id=? and phase=?",
                (rs, n) -> {
                    T value = SettlementBindingJson.read(rs.getString(1), type);
                    UUID storedId = value instanceof SettlementBinding.Instruction i ? i.intentId() :
                            value instanceof SettlementBinding.Review r ? r.recommendationId() :
                            ((SettlementBinding.Execution) value).executionId();
                    if (!id.equals(storedId) || !SettlementBindingJson.fingerprint(value).equals(rs.getString(2)))
                        throw new IllegalStateException("SETTLEMENT_BINDING_INTEGRITY_FAILED");
                    return value;
                }, id, phase);
        if (values.size() > 1) throw new IllegalStateException("SETTLEMENT_BINDING_INTEGRITY_FAILED");
        return values.stream().findFirst();
    }
}
