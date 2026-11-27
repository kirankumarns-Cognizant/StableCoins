package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

/** Additive runtime holder, never a competing legacy BlockchainAdapter/WalletSigningService bean. */
public final class BesuRuntime implements AutoCloseable {
    private final BesuBlockchainAdapter adapter;
    private final BesuHttpRpc transport;
    private final SettlementNetworkRegistry registry;
    private final Clock clock;

    public BesuRuntime(BesuBlockchainAdapter adapter, BesuHttpRpc transport, Clock clock) {
        this.adapter = adapter;
        this.transport = transport;
        this.clock = clock;
        // Deliberately closed to payment creation until complete bindings/accounting acceptance.
        registry = new SettlementNetworkRegistry(List.of(new SettlementNetworkRegistry.Entry(adapter.context(), false)));
    }
    public SettlementNetwork.Context forNewWork(String id) { return registry.forNewWork(id); }

    /** Future persisted execution owner must provide its exact context, never current-default inference. */
    public BesuBlockchainAdapter forPinnedExecution(SettlementNetwork.Context pinned) {
        registry.forPinnedExecution(pinned);
        return adapter;
    }
    /**
     * Read-only evidence for exact owner material. Identity validation is allowed while new
     * work is disabled; it is not an execution claim and does not bypass the acceptance gate.
     */
    public SettlementObservation observe(SettlementBinding.Instruction instruction) {
        java.util.Objects.requireNonNull(instruction, "INSTRUCTION_REQUIRED");
        registry.forPinnedExecution(instruction.context());
        if (!clock.instant().isBefore(instruction.deliverBy()))
            throw new IllegalStateException("SETTLEMENT_INSTRUCTION_DEADLINE_PASSED");
        var raw = observe(instruction.recipient(), instruction.tokenAmount());
        Instant expires = raw.expiresAt().isBefore(instruction.deliverBy())
                ? raw.expiresAt() : instruction.deliverBy();
        var result = new SettlementObservation(instruction, raw.block(), raw.tokenTotal(), raw.nativeTotal(),
                raw.estimatedGas(), bufferedGas(raw.estimatedGas()), raw.gasPriceWei(),
                raw.requiredNativeEstimate(), raw.observedAt(), expires);
        result.requireFor(instruction, clock.instant());
        return result;
    }
    private static BigDecimal bufferedGas(BigDecimal estimate) {
        return estimate.multiply(new BigDecimal("1.2")).setScale(0, java.math.RoundingMode.CEILING);
    }
    public Observation observe(String recipient, BigDecimal amount) {
        adapter.validateObservationRequest(recipient, amount);
        // No nonce reservation, signing, submission, approval or financial journal.
        Instant observed = clock.instant();
        long block = adapter.getCurrentBlockNumber();
        var gas = adapter.estimateTransferGas(adapter.getFromAddress(), recipient,
                adapter.context().token().contract(), amount, 6);
        var price = adapter.getGasPriceWei();
        var token = adapter.getTokenBalance(adapter.getFromAddress(), adapter.context().token().contract(), 6);
        var nativeBalance = adapter.getNativeBalance(adapter.getFromAddress());
        var required = bufferedGas(gas)
                .multiply(price).movePointLeft(18);
        if (!clock.instant().isBefore(observed.plusSeconds(15)) || clock.instant().isBefore(observed))
            throw new IllegalStateException("BESU_OBSERVATION_WINDOW_EXPIRED");
        return new Observation(adapter.context(), adapter.classification(), "LIVE", block, token,
                nativeBalance, gas, price, required,
                token.compareTo(amount) >= 0 && nativeBalance.compareTo(required) >= 0,
                observed, observed.plusSeconds(15), "PAYMENT_INTEGRATION_NOT_ENABLED");
    }
    public record Observation(SettlementNetwork.Context context, String classification, String evidenceMode,
                              long block, BigDecimal tokenTotal, BigDecimal nativeTotal,
                              BigDecimal estimatedGas, BigDecimal gasPriceWei, BigDecimal requiredNativeEstimate,
                              boolean balancesCoverEstimate, Instant observedAt, Instant expiresAt,
                              String executionGate) {}
    @Override public void close() { transport.close(); }
}
