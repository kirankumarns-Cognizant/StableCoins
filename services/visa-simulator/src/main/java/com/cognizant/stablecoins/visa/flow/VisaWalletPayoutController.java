package com.cognizant.stablecoins.visa.flow;

import com.cognizant.stablecoins.flow.SettlementNetwork;
import com.cognizant.stablecoins.platform.security.AccessFailure;
import com.cognizant.stablecoins.platform.security.InternalServiceAccess;
import com.cognizant.stablecoins.visa.liquidity.VisaPaymentAdapter;
import com.cognizant.stablecoins.visa.liquidity.WalletPayouts;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Internal simulator harness, not a customer payment/approval API. No chain transfer is performed. */
@RestController
@RequestMapping("/visa-simulator/v1/wallet")
@ConditionalOnProperty(name="visa.simulator.wallet.enabled", havingValue="true")
public class VisaWalletPayoutController {
    private final VisaPaymentAdapter adapter;
    private final InternalServiceAccess internal;
    private final Environment env;
    public VisaWalletPayoutController(VisaPaymentAdapter adapter, InternalServiceAccess internal, Environment env) {
        this.adapter = adapter;
        this.internal = internal;
        this.env = env;
    }
    public record Destination(String providerId, SettlementNetwork.Token token) {}
    public record Funding(String providerId, SettlementNetwork.Token token, BigDecimal amount) {}
    public record Resolution(Boolean succeeded) {}
    private void operations(Authentication auth) {
        internal.require(auth);
        if (!env.getProperty("visa.simulator.operations-enabled", Boolean.class, false))
            throw new AccessFailure("SIMULATOR_OPERATIONS_DISABLED", 403);
    }
    @PostMapping("/balance")
    public WalletPayouts.Balance balance(@RequestBody Destination body, Authentication auth) {
        operations(auth);
        return adapter.walletBalance(body.providerId(), body.token());
    }
    @PostMapping("/prefund")
    public WalletPayouts.Balance prefund(@RequestHeader("Idempotency-Key") UUID key,
            @RequestBody Funding body, Authentication auth) {
        operations(auth);
        return adapter.prefundWallet(key, body.providerId(), body.token(), body.amount());
    }
    @PostMapping("/payouts")
    public WalletPayouts.Payout create(@RequestHeader("Idempotency-Key") UUID key,
            @RequestBody WalletPayouts.Request body, Authentication auth) {
        operations(auth);
        if (!key.equals(body.executionId())) throw new IllegalArgumentException("WALLET_IDEMPOTENCY_KEY_MISMATCH");
        return adapter.createWalletPayout(body);
    }
    @GetMapping("/payouts/{id}")
    public WalletPayouts.Payout get(@PathVariable UUID id, Authentication auth) {
        operations(auth);
        return adapter.walletPayout(id);
    }
    @GetMapping("/payouts/{id}/balances")
    public WalletPayouts.Statement balances(@PathVariable UUID id, Authentication auth) {
        operations(auth);
        return adapter.walletPayoutStatement(id);
    }
    @PostMapping("/payouts/{id}/complete")
    public WalletPayouts.Payout complete(@PathVariable UUID id, Authentication auth) {
        operations(auth);
        return adapter.finishWalletPayout(id);
    }
    @PostMapping("/payouts/{id}/resolution")
    public WalletPayouts.Payout resolve(@PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody Resolution body, Authentication auth) {
        operations(auth);
        if (body.succeeded() == null) throw new IllegalArgumentException("WALLET_PAYOUT_OUTCOME_REQUIRED");
        return adapter.resolveWalletPayout(id, key, body.succeeded());
    }
}