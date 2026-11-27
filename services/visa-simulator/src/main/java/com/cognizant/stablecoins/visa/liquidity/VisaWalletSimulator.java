package com.cognizant.stablecoins.visa.liquidity;

import com.cognizant.stablecoins.flow.SettlementNetwork;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** No RPC, private key, ledger writer or transaction submitter. Called inside the adapter transaction. */
public final class VisaWalletSimulator {
    private final WalletPayoutStore store;
    private final Set<String> providers;
    private final Set<SettlementNetwork.Token> tokens;
    private final WalletPayouts.Scenario scenario;
    public VisaWalletSimulator(WalletPayoutStore store, Set<String> providers,
            Set<SettlementNetwork.Token> tokens, WalletPayouts.Scenario scenario) {
        this.store = java.util.Objects.requireNonNull(store);
        this.providers = Set.copyOf(providers);
        this.tokens = Set.copyOf(tokens);
        this.scenario = java.util.Objects.requireNonNull(scenario);
        for (var token : tokens) {
            var chain = token.chain();
            boolean supported = token.decimals() == 6 && (
                    (chain.classification() == SettlementNetwork.Classification.PUBLIC_TESTNET
                            && chain.networkId().equals("base-sepolia") && chain.chainId() == 84532
                            && token.symbol().equals("USDC")
                            && token.contract().equals("0x036cbd53842c5426634e7929541ec2318f3dcf7e"))
                    || (chain.classification() == SettlementNetwork.Classification.PRIVATE_DEMO
                            && chain.networkId().equals("besu-local") && chain.chainId() == 20261125
                            && token.symbol().equals("DEMOUSD")));
            if (!supported) throw new IllegalArgumentException("UNSUPPORTED_SIMULATED_WALLET_TOKEN");
        }
    }
    private void supported(String provider, SettlementNetwork.Token token) {
        if (provider == null || token == null || !providers.contains(provider) || !tokens.contains(token))
            throw new IllegalArgumentException("UNSUPPORTED_SIMULATED_WALLET_DESTINATION");
    }
    public WalletPayouts.Balance balance(String provider, SettlementNetwork.Token token) {
        supported(provider, token);
        return store.lockBalance(provider, token);
    }
    public WalletPayouts.Balance prefund(UUID operationId, String provider,
            SettlementNetwork.Token token, BigDecimal amount) {
        if (operationId == null) throw new IllegalArgumentException("WALLET_FUNDING_ID_REQUIRED");
        supported(provider, token);
        amount = WalletPayouts.quantity(token, amount);
        store.lockBalance(provider, token);
        store.prefund(operationId, provider, token, amount);
        return store.lockBalance(provider, token);
    }
    public WalletPayouts.Payout create(WalletPayouts.Request request) {
        if (request == null) throw new IllegalArgumentException("WALLET_REQUEST_REQUIRED");
        supported(request.providerId(), request.token());
        store.lockPayment(request.paymentId());
        if (store.hasFiatPayout(request.paymentId()))
            throw new IllegalStateException("SIMULATED_PAYOUT_DESTINATION_CONFLICT");
        var balance = store.lockBalance(request.providerId(), request.token());
        var existing = store.byPayment(request.paymentId());
        if (existing.isPresent()) {
            if (!existing.get().request().equals(request))
                throw new IllegalStateException("SIMULATED_WALLET_PAYOUT_CONFLICT");
            return existing.get(); // Changed scenario configuration cannot alter a captured operation.
        }
        var id = UUID.nameUUIDFromBytes((request.executionId() + "/visa-wallet").getBytes(StandardCharsets.UTF_8));
        var status = balance.available().compareTo(request.amount()) < 0 ? WalletPayouts.Status.FAILED
                : scenario == WalletPayouts.Scenario.TIMEOUT ? WalletPayouts.Status.UNKNOWN : WalletPayouts.Status.PROCESSING;
        var now = Instant.now();
        var payout = new WalletPayouts.Payout(id, request, "SIMULATED-VISA-WALLET-" + id,
                status, scenario, null, "SIMULATED", false, null, now, now);
        BigDecimal recipientBefore = store.recipientCredits(request.providerId(), request.token(), request.recipient());
        store.insert(payout);
        capture(payout, balance, recipientBefore, BigDecimal.ZERO);
        return payout;
    }
    public WalletPayouts.Payout get(UUID id) { return store.get(id); }
    public WalletPayouts.Statement statement(UUID id) {
        var payout = locked(id);
        var request = payout.request();
        return new WalletPayouts.Statement(payout, store.balanceChanges(id),
                store.lockBalance(request.providerId(), request.token()),
                store.recipientCredits(request.providerId(), request.token(), request.recipient()), "SIMULATED", false);
    }
    private void capture(WalletPayouts.Payout payout, WalletPayouts.Balance before,
            BigDecimal recipientBefore, BigDecimal movement) {
        var r = payout.request();
        var after = store.lockBalance(r.providerId(), r.token());
        var recipientAfter = store.recipientCredits(r.providerId(), r.token(), r.recipient());
        store.recordBalanceChange(new WalletPayouts.BalanceChange(payout.payoutId(), payout.status(), before, after,
                recipientBefore, recipientAfter, movement, movement, "SIMULATED", Instant.now()));
    }
    private WalletPayouts.Payout locked(UUID id) {
        var first = store.get(id);
        supported(first.request().providerId(), first.request().token());
        store.lockPayment(first.request().paymentId());
        store.lockBalance(first.request().providerId(), first.request().token());
        return store.get(id); // Re-read after lock. Timeout capacity never expires implicitly.
    }
    public WalletPayouts.Payout finish(UUID id) {
        var payout = locked(id);
        if (payout.status() != WalletPayouts.Status.PROCESSING) return payout;
        return finish(payout, payout.scenario() == WalletPayouts.Scenario.SUCCEEDED, null);
    }
    public WalletPayouts.Payout resolve(UUID id, UUID resolutionId, boolean success) {
        if (resolutionId == null) throw new IllegalArgumentException("WALLET_RESOLUTION_ID_REQUIRED");
        var payout = locked(id);
        var terminal = success ? WalletPayouts.Status.SUCCEEDED : WalletPayouts.Status.FAILED;
        if (payout.resolutionId() != null) {
            if (!payout.resolutionId().equals(resolutionId) || payout.status() != terminal)
                throw new IllegalStateException("WALLET_PAYOUT_RESOLUTION_CONFLICT");
            return payout;
        }
        if (payout.status() != WalletPayouts.Status.UNKNOWN)
            throw new IllegalStateException("WALLET_PAYOUT_NOT_UNKNOWN");
        return finish(payout, success, resolutionId);
    }
    private WalletPayouts.Payout finish(WalletPayouts.Payout prior, boolean success, UUID resolution) {
        var next = new WalletPayouts.Payout(prior.payoutId(), prior.request(), prior.providerReference(),
                success ? WalletPayouts.Status.SUCCEEDED : WalletPayouts.Status.FAILED, prior.scenario(),
                resolution, "SIMULATED", false, null, prior.createdAt(), Instant.now());
        var r = prior.request();
        var before = store.lockBalance(r.providerId(), r.token());
        var recipientBefore = store.recipientCredits(r.providerId(), r.token(), r.recipient());
        store.transition(next, success);
        capture(next, before, recipientBefore, success ? r.amount() : BigDecimal.ZERO);
        return next;
    }
}