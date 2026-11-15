package com.cognizant.stablecoins.payment.support;

import com.cognizant.stablecoins.payment.api.dto.*;
import com.cognizant.stablecoins.payment.domain.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.payment.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class IntentFixtures {
    public static final UUID CUSTOMER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final AuthorizedCustomer ACTOR = new AuthorizedCustomer(CUSTOMER, "verified-test-subject");
    public static final Instant DEADLINE = Instant.parse("2099-01-01T00:00:00Z");
    private IntentFixtures() { }
    public static PaymentIntentRequest intent(BeneficiaryOutcome outcome) {
        return new PaymentIntentRequest(outcome, DEADLINE, new BigDecimal("25.00"),
                SupportedCurrency.USD, RoutingPriority.MEET_DEADLINE, Set.of(), true);
    }
    public static CreatePaymentRequest request() {
        CreatePaymentRequest r = new CreatePaymentRequest();
        r.setSenderAccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        r.setBeneficiaryId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        r.setAmount(new BigDecimal("10000.00")); r.setCurrency(SupportedCurrency.USD);
        r.setPurpose(PaymentPurpose.SUPPLIER_INVOICE); r.setRequestedRail(PaymentRail.AUTO);
        r.setIntent(intent(BeneficiaryOutcome.SIMULATED_BANK_INR)); return r;
    }
    public static Account account() {
        Account a = new Account(); a.setCustomerId(CUSTOMER); a.setStatus("ACTIVE");
        a.setCurrency("USD"); return a;
    }
    public static Beneficiary beneficiary() {
        Beneficiary b = new Beneficiary(); b.setCustomerId(CUSTOMER); b.setStatus("ACTIVE");
        b.setCurrency("INR"); b.setWalletAddress("0x1111111111111111111111111111111111111111"); return b;
    }
    public static CustomerRoutingPolicy policy(String version) {
        return new CustomerRoutingPolicy(version, EnumSet.allOf(BeneficiaryOutcome.class),
                Set.of("SIMULATED-PROVIDER-A", "SIMULATED-PROVIDER-B"), new BigDecimal("50.00"), 2);
    }
    public static RoutingPolicyProperties policies() {
        RoutingPolicyProperties p = new RoutingPolicyProperties();
        p.setPolicies(Map.of(CUSTOMER, policy("test-v1"))); return p;
    }
}
