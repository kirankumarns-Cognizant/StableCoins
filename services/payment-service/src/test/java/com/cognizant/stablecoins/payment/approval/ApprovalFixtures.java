package com.cognizant.stablecoins.payment.approval;
import com.cognizant.stablecoins.payment.routing.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.domain.*;
import com.cognizant.stablecoins.payment.model.Payment;
import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import com.cognizant.stablecoins.payment.support.IntentFixtures;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
final class ApprovalFixtures {
    static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    static final RoutingEvidence.Network NETWORK=new RoutingEvidence.Network("base-sepolia",84532,"0x036CbD53842c5426634e7929541eC2318f3dCF7e",true);
    static final AssessmentRules RULES=new AssessmentRules("rules-v1",Duration.ofMinutes(5),Duration.ofHours(24),100,95,
        new BigDecimal("0.95"),new BigDecimal("30"),new BigDecimal("100"),20,200);
    static AssessmentContext context(Payment p) { return new AssessmentContext(p.getPaymentId(),p.getCorrelationId(),p.getIntent().customerId(),"US-IN",
        p.getAmount(),new BigDecimal("10000"),new BigDecimal("0.0002"),Map.of(BeneficiaryOutcome.SIMULATED_BANK_INR,new BigDecimal("830000.00")),PaymentRail.AUTO); }
    static RoutingEvidence.Snapshot evidence(AssessmentContext c) { return SimulatedRoutingScenarios.create(SimulatedRoutingScenarios.Scenario.NORMAL,c,BeneficiaryOutcome.SIMULATED_BANK_INR,NETWORK,NOW); }
    static Payment payment() {
        Payment p=new Payment(); p.setPaymentId("PAY-REVIEW");p.setCorrelationId("CORR-REVIEW");
        p.setSenderAccountId(IntentFixtures.request().getSenderAccountId());p.setBeneficiaryId(IntentFixtures.request().getBeneficiaryId());
        p.setAmount(new BigDecimal("10000.00"));p.setSourceCurrency("USD");p.setDestinationCurrency("INR");p.setPaymentRail("AUTO");
        p.setPurpose("SUPPLIER_INVOICE");p.setStatus("COMPLIANCE_APPROVED");p.setIdempotencyKey(UUID.randomUUID().toString());p.setRequestFingerprint("a".repeat(64));
        p.setIntent(new PaymentIntentSnapshot(UUID.randomUUID(),1,IntentFixtures.CUSTOMER,"creator",NOW.minusSeconds(10),SettlementAsset.USDC,true,true,
            new PaymentIntentRequest(BeneficiaryOutcome.SIMULATED_BANK_INR,NOW.plusSeconds(300),new BigDecimal("25.00"),SupportedCurrency.USD,RoutingPriority.LOWEST_COST,Set.of(),true),IntentFixtures.policy("test-v1")));
        return p;
    }
    static RecommendationDocument.Quote quote(AssessmentContext c) {
        return new RecommendationDocument.Quote(UUID.randomUUID(),1,c.paymentId(),c.correlationId(),c.customerId(),"SIMULATED-A","SIMULATED-PROVIDER-A",
            c.sourceAmountUsd(),c.transferAmountUsdc(),c.requiredPayoutAmounts(),new BigDecimal("5.30"),NOW.minusSeconds(5),NOW.plusSeconds(100),true);
    }
}
