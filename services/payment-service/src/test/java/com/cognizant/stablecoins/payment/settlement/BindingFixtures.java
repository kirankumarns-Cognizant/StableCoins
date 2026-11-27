package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static com.cognizant.stablecoins.flow.SettlementBinding.*;
import static com.cognizant.stablecoins.flow.SettlementNetwork.*;

final class BindingFixtures {
    private BindingFixtures() {}
    static String hash(String s) { return "0x" + s.repeat(64); }
    static String address(String s) { return "0x" + s.repeat(40); }
    static Instruction instruction() {
        var chain = new Chain("besu-local", 20261125, hash("a"), Classification.PRIVATE_DEMO);
        var context = new Context(new Token(chain, address("b"), "DEMOUSD", 6, hash("c")),
                new NativeAsset(chain, "BESU_GAS", 18), address("d"), FeeModel.EXECUTION_ONLY, "besu-demo-v1");
        return new Instruction(UUID.randomUUID(), 1, UUID.randomUUID(), "PAY-" + UUID.randomUUID(),
                "CORR-" + UUID.randomUUID(), context, address("e"), new BigDecimal("5"),
                new BigDecimal("5"), Outcome.TOKEN_WALLET, new BigDecimal("3"), Instant.parse("2099-01-01T00:00:00Z"));
    }
    static Review review(Instruction i) {
        return new Review(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), 1,
                "SIMULATED-PROVIDER-A", i, new BigDecimal("2"), new BigDecimal("0.000001"),
                Instant.parse("2026-11-25T10:00:00Z"), Instant.parse("2026-11-25T10:15:00Z"));
    }
    static Instruction changedRecipient(Instruction i) {
        return new Instruction(i.intentId(), i.intentVersion(), i.customerId(), i.paymentId(),
                i.correlationId(), i.context(), address("f"), i.sourceUsd(), i.tokenAmount(),
                i.outcome(), i.maximumFeeUsd(), i.deliverBy());
    }
}
