package com.cognizant.stablecoins.payment.integration;

import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.UUID;
import static com.cognizant.stablecoins.payment.support.IntentFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL migration + JSONB round trip. Requires Docker; never uses a mock database. */
@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true)
class PaymentIntentPersistenceIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired EntityManager em;
    @Test void migratesAndPersistsPolicyAndIntentWithSeparateAssetAndFiatFields() {
        Customer c=new Customer();c.setCustomerId(CUSTOMER);c.setName("Fictional intent customer");c.setCustomerType("CORPORATE");
        // Generated UUID belongs to JPA; use the generated identity for FK records and context.
        c.setCustomerId(null);em.persist(c);em.flush();
        Account a=account();a.setCustomerId(c.getCustomerId());a.setAccountType("OPERATING");a.setBalance(new BigDecimal("250000"));em.persist(a);
        Beneficiary b=beneficiary();b.setCustomerId(c.getCustomerId());b.setName("Fictional supplier");b.setCountry("IND");em.persist(b);em.flush();
        var registry=policies();registry.setPolicies(java.util.Map.of(c.getCustomerId(),policy("db-v1")));
        Payment p=new Payment();p.setPaymentId("PAY-"+UUID.randomUUID());p.setCorrelationId("CORR-"+UUID.randomUUID());
        p.setSenderAccountId(a.getAccountId());p.setBeneficiaryId(b.getBeneficiaryId());p.setAmount(new BigDecimal("10000.00"));
        p.setSourceCurrency("USD");p.setDestinationCurrency(null);p.setPaymentRail("AUTO");p.setPurpose("SUPPLIER_INVOICE");
        p.setStatus("COMPLIANCE_PENDING");p.setIdempotencyKey(UUID.randomUUID().toString());p.setRequestFingerprint("0".repeat(64));
        var r=request();r.setSenderAccountId(a.getAccountId());r.setBeneficiaryId(b.getBeneficiaryId());r.setIntent(intent(BeneficiaryOutcome.USDC_WALLET));
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            p.setIntent(new PaymentIntentService(factory.getValidator(),registry).prepare(r,a,b,new AuthorizedCustomer(c.getCustomerId(),"db-test-subject")));
        }
        em.persist(p);em.flush();var expected=p.getIntent();em.clear();
        Payment loaded=em.find(Payment.class,p.getPaymentId());
        assertThat(loaded.getIntent()).isEqualTo(expected);assertThat(loaded.getDestinationCurrency()).isNull();
        assertThat(loaded.getIntent().settlementAsset()).isEqualTo(SettlementAsset.USDC);
    }
}
