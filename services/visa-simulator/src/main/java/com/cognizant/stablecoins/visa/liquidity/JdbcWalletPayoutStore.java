package com.cognizant.stablecoins.visa.liquidity;

import com.cognizant.stablecoins.flow.SettlementNetwork;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable provider-owned records, isolated from fiat pockets and application financial journals. */
@Repository
public class JdbcWalletPayoutStore implements WalletPayoutStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public JdbcWalletPayoutStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("WALLET_DOCUMENT_INVALID"); }
    }
    private String key(String provider, SettlementNetwork.Token token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((provider + "/" + json(token)).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override public void lockPayment(String payment) {
        lock("visa-payment:" + payment);
    }
    private void lock(String scope) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, scope);
    }
    @Override public boolean hasFiatPayout(String payment) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from visa_transaction where payment_id=?)", Boolean.class, payment));
    }
    @Override public WalletPayouts.Balance lockBalance(String provider, SettlementNetwork.Token token) {
        String id = key(provider, token);
        jdbc.update("insert into visa_wallet_pocket(pocket_id,provider_id,token_document,total) values (?,?,cast(? as jsonb),0) on conflict do nothing",
                id, provider, json(token));
        BigDecimal total = jdbc.queryForObject("select total from visa_wallet_pocket where pocket_id=? for update", BigDecimal.class, id);
        BigDecimal reserved = jdbc.queryForObject(
                "select coalesce(sum(amount),0) from visa_wallet_payout where pocket_id=? and status in ('PROCESSING','UNKNOWN')",
                BigDecimal.class, id);
        return new WalletPayouts.Balance(provider, token, total, reserved, total.subtract(reserved),
                "SIMULATED", Instant.now());
    }
    @Override public void prefund(UUID operationId, String provider, SettlementNetwork.Token token, BigDecimal amount) {
        String id = key(provider, token);
        lock("visa-wallet-funding:" + operationId);
        var prior = jdbc.queryForList("select pocket_id,amount from visa_wallet_funding where operation_id=?", operationId);
        if (!prior.isEmpty()) {
            if (!id.equals(prior.getFirst().get("pocket_id"))
                    || ((BigDecimal) prior.getFirst().get("amount")).compareTo(amount) != 0)
                throw new IllegalStateException("SIMULATED_WALLET_FUNDING_CONFLICT");
            return;
        }
        var balance = lockBalance(provider, token);
        WalletPayouts.quantity(token, balance.total().add(amount));
        jdbc.update("insert into visa_wallet_funding(operation_id,pocket_id,amount) values (?,?,?)", operationId, id, amount);
        jdbc.update("update visa_wallet_pocket set total=total+? where pocket_id=?", amount, id);
    }
    private WalletPayouts.Payout read(ResultSet rs, int index) throws SQLException {
        WalletPayouts.Request request;
        try { request = mapper.readValue(rs.getString("request_document"), WalletPayouts.Request.class); }
        catch (Exception e) { throw new IllegalStateException("WALLET_DOCUMENT_INVALID"); }
        if (!request.executionId().equals(rs.getObject("execution_id", UUID.class))
                || !request.paymentId().equals(rs.getString("payment_id"))
                || !request.correlationId().equals(rs.getString("correlation_id"))
                || request.amount().compareTo(rs.getBigDecimal("amount")) != 0
                || !key(request.providerId(), request.token()).equals(rs.getString("pocket_id")))
            throw new IllegalStateException("WALLET_STORED_BINDING_CHANGED");
        return new WalletPayouts.Payout(rs.getObject("payout_id", UUID.class), request, rs.getString("reference"),
                WalletPayouts.Status.valueOf(rs.getString("status")), WalletPayouts.Scenario.valueOf(rs.getString("scenario")),
                rs.getObject("resolution_id", UUID.class), rs.getString("evidence_mode"), false, null,
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
    @Override public Optional<WalletPayouts.Payout> byPayment(String payment) {
        return jdbc.query("select * from visa_wallet_payout where payment_id=?", this::read, payment).stream().findFirst();
    }
    @Override public WalletPayouts.Payout get(UUID id) {
        if (id == null) throw new IllegalArgumentException("WALLET_PAYOUT_ID_REQUIRED");
        return jdbc.query("select * from visa_wallet_payout where payout_id=?", this::read, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("WALLET_PAYOUT_NOT_FOUND"));
    }
    @Override public BigDecimal recipientCredits(String provider, SettlementNetwork.Token token, String recipient) {
        return jdbc.queryForObject("select coalesce(sum(amount),0) from visa_wallet_payout where pocket_id=? and status='SUCCEEDED' and request_document->>'recipient'=?",
                BigDecimal.class, key(provider, token), recipient);
    }
    @Override public void recordBalanceChange(WalletPayouts.BalanceChange change) {
        jdbc.update("insert into visa_wallet_balance_change(payout_id,status,document) values (?,?,cast(? as jsonb))",
                change.payoutId(), change.status().name(), json(change));
    }
    @Override public java.util.List<WalletPayouts.BalanceChange> balanceChanges(UUID payoutId) {
        return jdbc.query("select document from visa_wallet_balance_change where payout_id=? order by sequence_id",
                (rs,index) -> {
                    try { return mapper.readValue(rs.getString("document"), WalletPayouts.BalanceChange.class); }
                    catch (Exception e) { throw new IllegalStateException("WALLET_BALANCE_EVIDENCE_INVALID"); }
                }, payoutId);
    }
    @Override public void insert(WalletPayouts.Payout p) {
        var r = p.request();
        jdbc.update("insert into visa_wallet_payout(payout_id,execution_id,payment_id,correlation_id,pocket_id,amount,request_document,reference,status,scenario,evidence_mode,created_at,updated_at) values (?,?,?,?,?,?,cast(? as jsonb),?,?,?,'SIMULATED',?,?)",
                p.payoutId(), r.executionId(), r.paymentId(), r.correlationId(), key(r.providerId(), r.token()), r.amount(),
                json(r), p.providerReference(), p.status().name(), p.scenario().name(),
                java.sql.Timestamp.from(p.createdAt()), java.sql.Timestamp.from(p.updatedAt()));
        audit(p);
    }
    @Override public void transition(WalletPayouts.Payout p, boolean consume) {
        var prior = get(p.payoutId());
        if (!prior.request().equals(p.request()) || !java.util.Set.of(WalletPayouts.Status.PROCESSING, WalletPayouts.Status.UNKNOWN).contains(prior.status())
                || (consume != (p.status() == WalletPayouts.Status.SUCCEEDED)))
            throw new IllegalStateException("SIMULATED_WALLET_TRANSITION_CONFLICT");
        if (consume && jdbc.update("update visa_wallet_pocket set total=total-? where pocket_id=? and total>=?",
                p.request().amount(), key(p.request().providerId(), p.request().token()), p.request().amount()) != 1)
            throw new IllegalStateException("SIMULATED_WALLET_BALANCE_CONFLICT");
        if (jdbc.update("update visa_wallet_payout set status=?,resolution_id=?,updated_at=? where payout_id=? and status in ('PROCESSING','UNKNOWN')",
                p.status().name(), p.resolutionId(), java.sql.Timestamp.from(p.updatedAt()), p.payoutId()) != 1)
            throw new IllegalStateException("SIMULATED_WALLET_TRANSITION_CONFLICT");
        audit(p);
    }
    private void audit(WalletPayouts.Payout p) {
        jdbc.update("insert into visa_wallet_payout_audit(payout_id,status) values (?,?)", p.payoutId(), p.status().name());
        org.slf4j.LoggerFactory.getLogger(getClass()).info(
                "service=visa-simulator event=simulated_wallet_payout paymentId={} correlationId={} payoutId={} status={} evidenceMode=SIMULATED",
                p.request().paymentId(), p.request().correlationId(), p.payoutId(), p.status());
    }
}