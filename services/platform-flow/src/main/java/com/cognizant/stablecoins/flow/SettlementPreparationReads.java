package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** ADR-007 read-only owner checks. No writes to another service's records. */
public final class SettlementPreparationReads {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public SettlementPreparationReads(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = Objects.requireNonNull(jdbc); this.mapper = Objects.requireNonNull(mapper).copy()
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public Optional<SettlementPreparation.Request> request(UUID id) {
        var rows = jdbc.query("select document::text,fingerprint from payment_settlement_preparation where request_id=?",
                (rs, n) -> {
                    var r = SettlementBindingJson.read(rs.getString(1), SettlementPreparation.Request.class);
                    if (!id.equals(r.requestId()) || !SettlementBindingJson.fingerprint(r).equals(rs.getString(2)))
                        throw new IllegalStateException("SETTLEMENT_PREPARATION_INTEGRITY_FAILED");
                    return r;
                }, id);
        if (rows.size() > 1) throw new IllegalStateException("SETTLEMENT_PREPARATION_INTEGRITY_FAILED");
        return rows.stream().findFirst();
    }
    public void requireRequested(SettlementPreparation.Request expected) {
        if (request(expected.requestId()).filter(expected::equals).isEmpty())
            throw new IllegalStateException("CANONICAL_SETTLEMENT_PREPARATION_REQUIRED");
        requireInstruction(expected.instruction());
    }
    public SettlementBinding.Instruction instruction(String paymentId) {
        var values = jdbc.query("select document::text,fingerprint from payment_settlement_binding where payment_id=? and phase='INSTRUCTION'",
                (rs,n) -> {
                    var i = SettlementBindingJson.read(rs.getString(1), SettlementBinding.Instruction.class);
                    if (!paymentId.equals(i.paymentId()) || !SettlementBindingJson.fingerprint(i).equals(rs.getString(2))) throw changed();
                    return i;
                }, paymentId);
        if (values.size() != 1) throw changed();
        return values.getFirst();
    }
    public void requireInstruction(SettlementBinding.Instruction i) {
        requireInstruction(i,"COMPLIANCE_APPROVED");
    }
    public void requireFundedInstruction(SettlementBinding.Instruction i) {
        requireInstruction(i,"FUNDED");
    }
    private void requireInstruction(SettlementBinding.Instruction i,String requiredStatus) {
        if (!instruction(i.paymentId()).equals(i)) throw changed();
        var owners = jdbc.query("select p.correlation_id,p.amount,p.source_currency,p.payment_rail,p.status,p.intent_snapshot::text,a.customer_id,b.customer_id beneficiary_customer_id,a.status account_status,b.status beneficiary_status,b.wallet_address from payment p join account a on a.account_id=p.sender_account_id join beneficiary b on b.beneficiary_id=p.beneficiary_id where p.payment_id=?",
                (rs, n) -> i.correlationId().equals(rs.getString("correlation_id"))
                        && i.customerId().equals(rs.getObject("customer_id", UUID.class))
                        && i.customerId().equals(rs.getObject("beneficiary_customer_id", UUID.class))
                        && i.sourceUsd().compareTo(rs.getBigDecimal("amount")) == 0
                        && "USD".equals(rs.getString("source_currency"))
                        && "STABLECOIN".equals(rs.getString("payment_rail"))
                        && requiredStatus.equals(rs.getString("status"))
                        && "ACTIVE".equals(rs.getString("account_status"))
                        && "ACTIVE".equals(rs.getString("beneficiary_status"))
                        && i.recipient().equalsIgnoreCase(rs.getString("wallet_address"))
                        && matchesIntent(i, rs.getString("intent_snapshot")), i.paymentId());
        if (owners.size() != 1 || !owners.getFirst()) throw changed();
    }
    boolean matchesIntent(SettlementBinding.Instruction i, String json) {
        try {
            JsonNode intent = mapper.readTree(json);
            JsonNode body = intent.path("instruction"), confirmed = intent.path("settlement");
            var selected = body.path("settlement");
            JsonNode binding = mapper.readTree(SettlementBindingJson.write(i));
            return i.intentId().toString().equals(intent.path("intentId").asText())
                    && intent.path("intentVersion").isIntegralNumber() && i.intentVersion() == intent.path("intentVersion").intValue()
                    && i.customerId().toString().equals(intent.path("customerId").asText())
                    && binding.path("context").equals(confirmed.path("context"))
                    && i.recipient().equals(confirmed.path("recipient").asText())
                    && money(confirmed.path("tokenAmount"), i.tokenAmount())
                    && i.context().token().chain().networkId().equals(selected.path("networkId").asText())
                    && i.context().token().contract().equals(selected.path("tokenContract").asText())
                    && money(selected.path("tokenAmount"), i.tokenAmount())
                    && i.outcome().name().equals(body.path("requiredOutcome").asText())
                    && body.path("allowedAlternatives").isArray() && body.path("allowedAlternatives").isEmpty()
                    && money(body.path("maximumFee"), i.maximumFeeUsd())
                    && i.deliverBy().equals(java.time.Instant.parse(body.path("deliverBy").asText()));
        } catch (Exception error) { return false; }
    }
    private static boolean money(JsonNode node, java.math.BigDecimal value) {
        return node.isNumber() && node.decimalValue().compareTo(value) == 0;
    }
    private static IllegalStateException changed() { return new IllegalStateException("SETTLEMENT_OWNER_INSTRUCTION_CHANGED"); }
}
