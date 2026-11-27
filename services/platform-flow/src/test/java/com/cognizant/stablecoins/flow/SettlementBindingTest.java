package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.flow.SettlementBinding.*;
import static com.cognizant.stablecoins.flow.SettlementNetwork.*;

class SettlementBindingTest {
    static String address(String digit) { return "0x" + digit.repeat(40); }
    static String hash(String digit) { return "0x" + digit.repeat(64); }
    static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    static Context context() {
        var chain = new Chain("besu-local", 20261125, hash("a"), Classification.PRIVATE_DEMO);
        return new Context(new Token(chain, address("b"), "DEMOUSD", 6, hash("c")),
                new NativeAsset(chain, "BESU_GAS", 18), address("d"), FeeModel.EXECUTION_ONLY, "besu-demo-v1");
    }
    static Instruction instruction() {
        return new Instruction(UUID.randomUUID(), 1, UUID.randomUUID(), "PAY-NETWORK", "CORR-NETWORK",
                context(), address("e"), new BigDecimal("5.00"), new BigDecimal("5.000000"),
                Outcome.TOKEN_WALLET, new BigDecimal("3.00"), NOW.plusSeconds(3600));
    }
    static Review review(Instruction instruction) {
        return new Review(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), 1,
                "SIMULATED-PROVIDER-A", instruction, new BigDecimal("2.02"),
                new BigDecimal("0.000001"), NOW, NOW.plusSeconds(900));
    }
    @Test void everyStageRoundTripsWithCanonicalDecimalAmounts() {
        var i = instruction(); var r = review(i); var e = new Execution(UUID.randomUUID(), r);
        assertThat(SettlementBindingJson.read(SettlementBindingJson.write(i), Instruction.class)).isEqualTo(i);
        assertThat(SettlementBindingJson.read(SettlementBindingJson.write(r), Review.class)).isEqualTo(r);
        assertThat(SettlementBindingJson.read(SettlementBindingJson.write(e), Execution.class)).isEqualTo(e);
        assertThat(e.context()).isEqualTo(context());
        assertThat(i.tokenAmount()).isEqualTo(new BigDecimal("5"));
    }
    @Test void jsonbKeyOrderAndDecimalPresentationDoNotChangeFingerprint() throws Exception {
        var original = instruction();
        var mapper = new ObjectMapper().findAndRegisterModules();
        ObjectNode value = (ObjectNode) mapper.readTree(SettlementBindingJson.write(original));
        var reversed = mapper.createObjectNode();
        var names = new java.util.ArrayList<String>(); value.fieldNames().forEachRemaining(names::add);
        java.util.Collections.reverse(names); names.forEach(name -> reversed.set(name, value.get(name)));
        reversed.put("sourceUsd", new BigDecimal("5.0000"));
        var restored = SettlementBindingJson.read(reversed.toString(), Instruction.class);
        assertThat(SettlementBindingJson.fingerprint(restored)).isEqualTo(SettlementBindingJson.fingerprint(original));
    }
    @Test void changedNetworkAssetSignerPolicyRecipientOrAmountsCannotReuseApproval() throws Exception {
        var original = review(instruction());
        var mapper = new ObjectMapper().findAndRegisterModules();
        for (String path : List.of(
                "/instruction/context/token/chain/genesisHash", "/instruction/context/nativeAsset/chain/genesisHash",
                "/instruction/context/token/contract", "/instruction/context/token/codeHash",
                "/instruction/context/treasury", "/instruction/context/policyVersion",
                "/instruction/recipient", "/instruction/sourceUsd", "/instruction/tokenAmount",
                "/instruction/maximumFeeUsd", "/feeUsd", "/nativeFeeReserve", "/version", "/quoteVersion",
                "/quoteId", "/providerId", "/expiresAt")) {
            ObjectNode tree = (ObjectNode) mapper.readTree(SettlementBindingJson.write(original));
            // A reset must change BOTH chain identities together to remain a valid context.
            if (path.endsWith("genesisHash")) {
                ((ObjectNode) tree.at("/instruction/context/token/chain")).put("genesisHash", hash("f"));
                ((ObjectNode) tree.at("/instruction/context/nativeAsset/chain")).put("genesisHash", hash("f"));
            } else {
                var parent = (ObjectNode) tree.at(path.substring(0, path.lastIndexOf('/')));
                String field = path.substring(path.lastIndexOf('/') + 1);
                if (field.equals("contract") || field.equals("treasury") || field.equals("recipient"))
                    parent.put(field, address("f"));
                else if (field.equals("codeHash")) parent.put(field, hash("f"));
                else if (field.equals("sourceUsd") || field.equals("tokenAmount")) parent.put(field, 6);
                else if (field.equals("maximumFeeUsd")) parent.put(field, 4);
                else if (field.equals("feeUsd")) parent.put(field, 2.03);
                else if (field.equals("nativeFeeReserve")) parent.put(field, new BigDecimal("0.000002"));
                else if (field.equals("version") || field.equals("quoteVersion")) parent.put(field, 2);
                else if (field.equals("quoteId")) parent.put(field, UUID.randomUUID().toString());
                else if (field.equals("expiresAt")) parent.put(field, NOW.plusSeconds(800).toString());
                else parent.put(field, "changed-v2");
            }
            var changed = SettlementBindingJson.read(tree.toString(), Review.class);
            assertThat(SettlementBindingJson.fingerprint(changed)).as(path)
                    .isNotEqualTo(SettlementBindingJson.fingerprint(original));
            assertThatThrownBy(() -> new Execution(UUID.randomUUID(), original).requireSameReview(changed))
                    .as(path).hasMessage("SETTLEMENT_APPROVAL_BINDING_CHANGED");
        }
    }
    @Test void usdcOnlyOutcomeCannotBeSatisfiedByPrivateDemoToken() {
        var i = instruction();
        assertThatThrownBy(() -> new Instruction(i.intentId(), 1, i.customerId(), i.paymentId(),
                i.correlationId(), i.context(), i.recipient(), i.sourceUsd(), i.tokenAmount(),
                Outcome.USDC_WALLET, i.maximumFeeUsd(), i.deliverBy()))
                .hasMessage("USDC_OUTCOME_REQUIRES_PUBLIC_USDC");
    }
    @Test void sourceUsdAndTokenQuantityAreSeparateWithoutAnImpliedPeg() {
        var i = instruction();
        var different = new Instruction(i.intentId(), 1, i.customerId(), i.paymentId(), i.correlationId(),
                i.context(), i.recipient(), new BigDecimal("10"), new BigDecimal("4"),
                i.outcome(), i.maximumFeeUsd(), i.deliverBy());
        assertThat(different.sourceUsd()).isNotEqualByComparingTo(different.tokenAmount());
    }
    @Test void tokenPrecisionAndSelfSettlementFailClosed() {
        var i = instruction();
        assertThatThrownBy(() -> new Instruction(i.intentId(), 1, i.customerId(), i.paymentId(),
                i.correlationId(), i.context(), i.recipient(), i.sourceUsd(), new BigDecimal("1.0000001"),
                i.outcome(), i.maximumFeeUsd(), i.deliverBy())).hasMessage("INVALID_FLOW_AMOUNT");
        assertThatThrownBy(() -> new Instruction(i.intentId(), 1, i.customerId(), i.paymentId(),
                i.correlationId(), i.context(), i.context().treasury(), i.sourceUsd(), i.tokenAmount(),
                i.outcome(), i.maximumFeeUsd(), i.deliverBy())).hasMessage("SELF_SETTLEMENT_NOT_SUPPORTED");
    }
    @Test void feeCapsAndReviewLifetimeAreEnforced() {
        var i = instruction(); var r = review(i);
        assertThatThrownBy(() -> new Review(r.recommendationId(), 1, r.preparationId(), r.quoteId(), 1,
                r.providerId(), i, new BigDecimal("3.01"), r.nativeFeeReserve(), NOW, r.expiresAt()))
                .hasMessage("CUSTOMER_FEE_LIMIT_EXCEEDED");
        assertThatThrownBy(() -> new Review(r.recommendationId(), 1, r.preparationId(), r.quoteId(), 1,
                r.providerId(), i, r.feeUsd(), r.nativeFeeReserve(), NOW, i.deliverBy().plusSeconds(1)))
                .hasMessage("INVALID_REVIEW_WINDOW");
        r.requireFresh(NOW);
        assertThatThrownBy(() -> r.requireFresh(NOW.minusNanos(1))).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
        assertThatThrownBy(() -> r.requireFresh(r.expiresAt())).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
    }
    @Test void strictCodecRejectsUnknownDuplicateTrailingAndNullMaterial() {
        String valid = SettlementBindingJson.write(instruction());
        String quote = Character.toString(34);
        for (String invalid : List.of("null", valid + "{}",
                "{" + quote + "extra" + quote + ":1," + valid.substring(1),
                "{" + quote + "intentVersion" + quote + ":99," + valid.substring(1))) {
            assertThatThrownBy(() -> SettlementBindingJson.read(invalid, Instruction.class))
                    .hasMessage("INVALID_SETTLEMENT_BINDING");
        }
    }
    @Test void numericIdentifiersFractionalVersionsAndQuotedAmountsAreRejected() throws Exception {
        var mapper = new ObjectMapper();
        String valid = SettlementBindingJson.write(instruction());
        for (String field : List.of("paymentId", "intentVersion", "sourceUsd")) {
            ObjectNode value = (ObjectNode) mapper.readTree(valid);
            if (field.equals("paymentId")) value.put(field, 123);
            else if (field.equals("intentVersion")) value.put(field, new BigDecimal("1.5"));
            else value.put(field, "5");
            assertThatThrownBy(() -> SettlementBindingJson.read(value.toString(), Instruction.class))
                    .hasMessage("INVALID_SETTLEMENT_BINDING");
        }
        ObjectNode value = (ObjectNode) mapper.readTree(valid);
        ((ObjectNode) value.path("context")).put("policyVersion", 123);
        assertThatThrownBy(() -> SettlementBindingJson.read(value.toString(), Instruction.class))
                .hasMessage("INVALID_SETTLEMENT_BINDING");
    }
    @Test void candidateIdentityIncludesGenesisContractAndProvider() {
        var r = review(instruction());
        assertThat(r.candidateId()).contains("besu-local:20261125:", hash("a"), address("b"))
                .endsWith(":SIMULATED-PROVIDER-A");
    }
}
