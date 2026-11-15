package com.cognizant.stablecoins.payment.intent;

import com.cognizant.stablecoins.payment.api.dto.*;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.payment.domain.*;
import com.cognizant.stablecoins.payment.exception.*;
import com.cognizant.stablecoins.payment.model.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.support.IntentFixtures.*;
import static org.assertj.core.api.Assertions.*;

class PaymentIntentServiceTest {
    static ValidatorFactory factory;
    PaymentIntentService service;
    CreatePaymentRequest request;
    Account account;
    Beneficiary beneficiary;
    @BeforeAll static void validator() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidator() { factory.close(); }
    @BeforeEach void setUp() {
        service = new PaymentIntentService(factory.getValidator(), policies(),
                Clock.fixed(Instant.parse("2026-11-14T08:00:00Z"), ZoneOffset.UTC));
        request=request(); account=account(); beneficiary=beneficiary();
    }
    PaymentIntentSnapshot prepare() { return service.prepare(request,account,beneficiary,ACTOR); }
    void rejects(String code) {
        assertThatThrownBy(this::prepare).isInstanceOf(PaymentException.class)
                .extracting("errorCode").isEqualTo(code);
    }
    @Test void bindsServerPolicyIdentityAndStablecoinAsset() {
        PaymentIntentSnapshot s=prepare();
        assertThat(s.customerId()).isEqualTo(CUSTOMER);
        assertThat(s.confirmedBy()).isEqualTo(ACTOR.actorId());
        assertThat(s.intentVersion()).isEqualTo(1);
        assertThat(s.policy().version()).isEqualTo("test-v1");
        assertThat(s.policy().requiredApprovals()).isEqualTo(2);
        assertThat(s.settlementAsset()).isEqualTo(SettlementAsset.USDC);
        assertThat(s.fundingSimulated()).isTrue(); assertThat(s.fiatPayoutSimulated()).isTrue();
    }
    @Test void walletOutcomeHasNoFiatCurrency() {
        request.setIntent(intent(BeneficiaryOutcome.USDC_WALLET));
        assertThat(prepare().instruction().requiredOutcome().payoutCurrency()).isNull();
        assertThat(prepare().fiatPayoutSimulated()).isFalse();
    }
    @Test void usdPayoutIsExplicitlySimulated() {
        beneficiary.setCurrency("USD");request.setIntent(intent(BeneficiaryOutcome.SIMULATED_BANK_USD));
        assertThat(prepare().instruction().requiredOutcome().payoutCurrency()).isEqualTo("USD");
        assertThat(prepare().fiatPayoutSimulated()).isTrue();
    }
    @Test void requiresVerifiedActor() {
        assertThatThrownBy(()->service.prepare(request,account,beneficiary,null))
            .isInstanceOf(PaymentAccessException.class).extracting("errorCode").isEqualTo("AUTHENTICATION_REQUIRED");
    }
    @Test void rejectsForeignAccount() { account.setCustomerId(UUID.randomUUID());rejects("CUSTOMER_SCOPE_MISMATCH"); }
    @Test void rejectsForeignBeneficiary() { beneficiary.setCustomerId(UUID.randomUUID());rejects("CUSTOMER_SCOPE_MISMATCH"); }
    @Test void rejectsSourceCurrencyMismatch() { account.setCurrency("INR");rejects("SOURCE_CURRENCY_MISMATCH"); }
    @Test void rejectsPayoutCurrencyMismatch() { beneficiary.setCurrency("USD");rejects("PAYOUT_CURRENCY_MISMATCH"); }
    @Test void rejectsMissingWallet() {
        request.setIntent(intent(BeneficiaryOutcome.USDC_WALLET));beneficiary.setWalletAddress(null);
        rejects("BENEFICIARY_WALLET_REQUIRED");
    }
    @Test void rejectsUnconfiguredPolicy() {
        service=new PaymentIntentService(factory.getValidator(),new RoutingPolicyProperties());
        rejects("ROUTING_POLICY_NOT_CONFIGURED");
    }
    @Test void rejectsPolicyForbiddenOutcome() {
        RoutingPolicyProperties p=new RoutingPolicyProperties();p.setPolicies(Map.of(CUSTOMER,
            new CustomerRoutingPolicy("wallet-only",Set.of(BeneficiaryOutcome.USDC_WALLET),Set.of("provider"),new BigDecimal("50"),1)));
        service=new PaymentIntentService(factory.getValidator(),p);rejects("OUTCOME_NOT_ALLOWED");
    }
    @Test void rejectsPastDeadline() {
        PaymentIntentRequest i=request.getIntent();request.setIntent(new PaymentIntentRequest(i.requiredOutcome(),
            Instant.parse("2026-11-14T08:00:00Z"),i.maximumFee(),i.feeCurrency(),i.priority(),Set.of(),true));
        rejects("DEADLINE_EXPIRED");
    }
    @ParameterizedTest @ValueSource(strings={"-0.01","0.001","10000000000000000.00"})
    void rejectsInvalidFeePrecisionOrAmount(String fee) {
        PaymentIntentRequest i=request.getIntent();request.setIntent(new PaymentIntentRequest(i.requiredOutcome(),
            i.deliverBy(),new BigDecimal(fee),i.feeCurrency(),i.priority(),Set.of(),true));rejects("INVALID_INTENT");
    }
    @Test void rejectsFeeAbovePolicy() {
        PaymentIntentRequest i=request.getIntent();request.setIntent(new PaymentIntentRequest(i.requiredOutcome(),
            i.deliverBy(),new BigDecimal("50.01"),i.feeCurrency(),i.priority(),Set.of(),true));rejects("FEE_LIMIT_EXCEEDS_POLICY");
    }
    @Test void rejectsUnconfirmedExtractionDraft() {
        PaymentIntentRequest i=request.getIntent();request.setIntent(new PaymentIntentRequest(i.requiredOutcome(),
            i.deliverBy(),i.maximumFee(),i.feeCurrency(),i.priority(),Set.of(),false));rejects("INVALID_INTENT");
    }
    @Test void rejectsMissingAndNullNestedFields() {
        request.setIntent(new PaymentIntentRequest(null,null,null,null,null,null,null));rejects("INVALID_INTENT");
    }
    @Test void rejectsRailOutcomeConflict() { request.setRequestedRail(PaymentRail.STABLECOIN);rejects("RAIL_OUTCOME_MISMATCH"); }
    @Test void rejectsVisaOnlyForStablecoinIntent() { request.setRequestedRail(PaymentRail.VISA);rejects("RAIL_OUTCOME_MISMATCH"); }
    @Test void alternativesRequireAuto() {
        request.setRequestedRail(PaymentRail.STABLECOIN_PLUS_VISA);
        PaymentIntentRequest i=request.getIntent();request.setIntent(new PaymentIntentRequest(i.requiredOutcome(),
            i.deliverBy(),i.maximumFee(),i.feeCurrency(),i.priority(),Set.of(BeneficiaryOutcome.USDC_WALLET),true));
        rejects("ALTERNATIVES_REQUIRE_AUTO");
    }
    @Test void explicitAlternativeMustBeDeliverable() {
        PaymentIntentRequest i=request.getIntent();request.setIntent(new PaymentIntentRequest(i.requiredOutcome(),
            i.deliverBy(),i.maximumFee(),i.feeCurrency(),i.priority(),Set.of(BeneficiaryOutcome.USDC_WALLET),true));
        assertThat(prepare().instruction().allowedAlternatives()).containsExactly(BeneficiaryOutcome.USDC_WALLET);
        beneficiary.setWalletAddress("invalid");rejects("BENEFICIARY_WALLET_REQUIRED");
    }
    @Test void callerCannotMutateConfirmedAlternativesOrPolicy() {
        Set<BeneficiaryOutcome> alternatives=new HashSet<>();alternatives.add(BeneficiaryOutcome.USDC_WALLET);
        PaymentIntentRequest i=request.getIntent();PaymentIntentRequest copied=new PaymentIntentRequest(i.requiredOutcome(),
            i.deliverBy(),i.maximumFee(),i.feeCurrency(),i.priority(),alternatives,true);alternatives.clear();
        assertThat(copied.allowedAlternatives()).hasSize(1);
        assertThatThrownBy(()->prepare().policy().allowedOutcomes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void rejectsPolicyWithNoApproval() {
        assertThatThrownBy(()->new CustomerRoutingPolicy("v1",Set.of(BeneficiaryOutcome.USDC_WALLET),
            Set.of("provider"),BigDecimal.ZERO,0)).isInstanceOf(IllegalArgumentException.class);
    }
}
