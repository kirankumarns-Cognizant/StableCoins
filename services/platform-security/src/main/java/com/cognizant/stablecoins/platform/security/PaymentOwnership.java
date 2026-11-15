package com.cognizant.stablecoins.platform.security;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import java.util.UUID;

/** Read-only shared-schema lookup under ADR-007. Never writes another service's tables. */
public class PaymentOwnership {
    private final JdbcTemplate jdbc;
    private final VerifiedCustomerAccess customers;
    private final InternalServiceAccess internal;
    public PaymentOwnership(JdbcTemplate jdbc,VerifiedCustomerAccess customers,InternalServiceAccess internal){this.jdbc=jdbc;this.customers=customers;this.internal=internal;}
    public void require(String paymentId,Authentication authentication) {
        if(internal.allowed(authentication)) return;
        var actor=customers.require(authentication,CustomerMembershipProperties.Role.PAYMENT_OPERATOR);
        if(paymentId==null || !paymentId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) throw new AccessFailure("PAYMENT_NOT_FOUND",404);
        java.util.List<UUID> owners;
        try { owners=jdbc.query("select a.customer_id from payment p join account a on a.account_id=p.sender_account_id where p.payment_id=?",
                (rs,n)->rs.getObject(1,UUID.class),paymentId); }
        catch(org.springframework.dao.DataAccessException error){throw new AccessFailure("EVIDENCE_STORE_UNAVAILABLE",503);}
        if(owners.size()!=1 || !actor.customerId().equals(owners.getFirst())) throw new AccessFailure("PAYMENT_NOT_FOUND",404);
    }
}
