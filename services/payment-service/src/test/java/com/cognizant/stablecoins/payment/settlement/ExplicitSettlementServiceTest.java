package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.api.dto.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.exception.PaymentValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static com.cognizant.stablecoins.payment.support.IntentFixtures.*;
import static com.cognizant.stablecoins.flow.SettlementNetwork.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExplicitSettlementServiceTest {
    final SettlementSelectionProperties properties = new SettlementSelectionProperties();
    final SettlementBindingStore store = mock(SettlementBindingStore.class);
    final ExplicitSettlementService service = new ExplicitSettlementService(properties, store);
    final Context context = publicContext();
    CreatePaymentRequest request;

    static Context publicContext() {
        var chain = new Chain("base-sepolia", 84532, "0x" + "a".repeat(64), Classification.PUBLIC_TESTNET);
        return new Context(new Token(chain, "0x" + "b".repeat(40), "USDC", 6, "0x" + "c".repeat(64)),
                new NativeAsset(chain, "ETH", 18), "0x" + "d".repeat(40),
                FeeModel.BASE_L1_AND_EXECUTION, "public-v1");
    }
    @BeforeEach void setup() {
        request = request();
        request.setIntent(selected(new SettlementSelection("base-sepolia", context.token().contract(), request.getAmount())));
        properties.setNetworks(List.of(new SettlementNetworkRegistry.Entry(context, true)));
        properties.setAllowedNetworks(Map.of(CUSTOMER, Set.of("base-sepolia")));
    }
    PaymentIntentRequest selected(SettlementSelection selection) {
        var intent = intent(BeneficiaryOutcome.USDC_WALLET);
        return new PaymentIntentRequest(intent.requiredOutcome(), intent.deliverBy(), intent.maximumFee(),
                intent.feeCurrency(), intent.priority(), intent.allowedAlternatives(), intent.confirmed(), selection);
    }
    ConfirmedSettlement confirm() { return service.confirm(request, CUSTOMER, beneficiary().getWalletAddress()); }
    void rejects(String code) {
        assertThatThrownBy(this::confirm).isInstanceOf(PaymentValidationException.class)
                .extracting("errorCode").isEqualTo(code);
        verifyNoInteractions(store);
    }
    Payment payment() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var intents = new PaymentIntentService(factory.getValidator(), policies(), service);
            var p = new Payment();
            p.setPaymentId("PAY-BOUND"); p.setCorrelationId("CORR-BOUND"); p.setAmount(request.getAmount());
            p.setIntent(intents.prepare(request, account(), beneficiary(), ACTOR));
            return p;
        }
    }

    @Test void resolvesServerContextAndCapturesCanonicalOwnerInstruction() {
        var p = payment();
        assertThat(p.getIntent().settlement().context()).isEqualTo(context);
        var binding = ExplicitSettlementService.instruction(p);
        assertThat(binding.customerId()).isEqualTo(CUSTOMER);
        assertThat(binding.recipient()).isEqualTo(beneficiary().getWalletAddress());
        assertThat(binding.context()).isEqualTo(context);
        assertThat(binding.tokenAmount()).isEqualByComparingTo(request.getAmount());
        service.capture(p);
        verify(store).captureInstruction(binding);
    }
    @Test void rejectsUnconfiguredCustomer() { properties.setAllowedNetworks(Map.of()); rejects("SETTLEMENT_NETWORK_NOT_ALLOWED"); }
    @Test void rejectsMissingNetworkWithoutDefaulting() { properties.setNetworks(List.of()); rejects("SETTLEMENT_NETWORK_UNAVAILABLE"); }
    @Test void rejectsDisabledNetwork() {
        properties.setNetworks(List.of(new SettlementNetworkRegistry.Entry(context, false)));
        rejects("SETTLEMENT_NETWORK_UNAVAILABLE");
    }
    @Test void configurationCannotBypassBesuAcceptanceGate() {
        var privateContext = BindingFixtures.instruction().context();
        properties.setNetworks(List.of(new SettlementNetworkRegistry.Entry(privateContext, true)));
        properties.setAllowedNetworks(Map.of(CUSTOMER, Set.of("besu-local")));
        request.setIntent(selected(new SettlementSelection("besu-local", privateContext.token().contract(), request.getAmount())));
        rejects("BESU_PAYMENT_ACCEPTANCE_PENDING");
    }
    @Test void rejectsCallerTokenSubstitution() {
        request.setIntent(selected(new SettlementSelection("base-sepolia", "0x" + "f".repeat(40), request.getAmount())));
        rejects("SETTLEMENT_TOKEN_NOT_ALLOWED");
    }
    @Test void rejectsImplicitExchangeRate() {
        request.setIntent(selected(new SettlementSelection("base-sepolia", context.token().contract(), BigDecimal.ONE)));
        rejects("PUBLIC_USDC_NOTIONAL_AMOUNT_MISMATCH");
    }
    @Test void rejectsFiatOutcomeForExplicitWalletSelection() {
        var old = request.getIntent();
        request.setIntent(new PaymentIntentRequest(BeneficiaryOutcome.SIMULATED_BANK_USD, old.deliverBy(),
                old.maximumFee(), old.feeCurrency(), old.priority(), Set.of(), true, old.settlement()));
        rejects("SETTLEMENT_OUTCOME_NOT_SUPPORTED");
    }
    @Test void rejectsSelfTransferAndMissingRecipient() {
        assertThatThrownBy(() -> service.confirm(request, CUSTOMER, context.treasury()))
                .extracting("errorCode").isEqualTo("INVALID_SETTLEMENT_INSTRUCTION");
        assertThatThrownBy(() -> service.confirm(request, CUSTOMER, null))
                .extracting("errorCode").isEqualTo("INVALID_SETTLEMENT_INSTRUCTION");
    }
    @Test void neverFeedsBoundInstructionIntoLegacyEvidenceOrExecution() {
        var p = payment();
        assertThatThrownBy(() -> ExplicitSettlementService.requireLegacyWorkflow(p))
                .extracting("errorCode").isEqualTo("SETTLEMENT_WORKFLOW_ACCEPTANCE_PENDING");
        verifyNoInteractions(store);
    }
    @Test void historicalIntentJsonRoundTripsWithoutNewNullFields() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var legacy = new PaymentIntentSnapshot(UUID.randomUUID(), 1, CUSTOMER, ACTOR.actorId(),
                java.time.Instant.now(), SettlementAsset.USDC, true, false,
                intent(BeneficiaryOutcome.USDC_WALLET), policy("v1"));
        String json = mapper.writeValueAsString(legacy);
        assertThat(json).doesNotContain("\"settlement\"");
        assertThat(mapper.readValue(json, PaymentIntentSnapshot.class)).isEqualTo(legacy);
        var p = new Payment(); p.setIntent(legacy);
        ExplicitSettlementService.requireLegacyWorkflow(p);
        service.capture(p); verifyNoInteractions(store);
    }
    @Test void selectedIntentJsonPreservesFullContextAndRecipient() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var intent = payment().getIntent();
        assertThat(mapper.readValue(mapper.writeValueAsString(intent), PaymentIntentSnapshot.class)).isEqualTo(intent);
    }
    @Test void semanticallyEqualSelectionHasStableFingerprintMaterial() {
        assertThat(new SettlementSelection("base-sepolia", context.token().contract().toUpperCase().replace("0X", "0x"), new BigDecimal("5.000000")).fingerprintMaterial())
                .isEqualTo(new SettlementSelection("base-sepolia", context.token().contract(), new BigDecimal("5")).fingerprintMaterial());
        assertThatThrownBy(() -> new SettlementSelection("base-sepolia", context.token().contract(), new BigDecimal("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsSelectedIntentWithMissingConfirmedContext(){var p=payment();var i=p.getIntent();p.setIntent(new PaymentIntentSnapshot(i.intentId(),i.intentVersion(),i.customerId(),i.confirmedBy(),i.confirmedAt(),i.settlementAsset(),i.fundingSimulated(),i.fiatPayoutSimulated(),i.instruction(),i.policy()));assertThatThrownBy(()->service.capture(p)).extracting("errorCode").isEqualTo("SETTLEMENT_INTENT_BINDING_CHANGED");verifyNoInteractions(store);}
    @Test void springBindsTypedContextAndCustomerAllowlist(){var values=new HashMap<String,Object>();String root="app.settlement.networks[0].context.";for(String part:List.of("token.chain.","native-asset.chain.")){values.put(root+part+"network-id","base-sepolia");values.put(root+part+"chain-id","84532");values.put(root+part+"genesis-hash",context.token().chain().genesisHash());values.put(root+part+"classification","PUBLIC_TESTNET");}values.put(root+"token.contract",context.token().contract());values.put(root+"token.symbol","USDC");values.put(root+"token.decimals","6");values.put(root+"token.code-hash",context.token().codeHash());values.put(root+"native-asset.symbol","ETH");values.put(root+"native-asset.decimals","18");values.put(root+"treasury",context.treasury());values.put(root+"fee-model","BASE_L1_AND_EXECUTION");values.put(root+"policy-version","public-v1");values.put("app.settlement.networks[0].enabled-for-new-work","true");values.put("app.settlement.allowed-networks."+CUSTOMER+"[0]","base-sepolia");var bound=new org.springframework.boot.context.properties.bind.Binder(new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(values)).bind("app.settlement",org.springframework.boot.context.properties.bind.Bindable.of(SettlementSelectionProperties.class)).get();assertThat(bound.getNetworks()).containsExactly(new SettlementNetworkRegistry.Entry(context,true));assertThat(bound.getAllowedNetworks()).containsEntry(CUSTOMER,Set.of("base-sepolia"));}
}
