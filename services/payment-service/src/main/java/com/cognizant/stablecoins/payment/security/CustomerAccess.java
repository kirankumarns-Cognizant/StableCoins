package com.cognizant.stablecoins.payment.security;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.exception.PaymentAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role;
/** Only verified JWT subjects and server-managed membership grant customer access. */
@Component
public class CustomerAccess {
    private final CustomerAccessProperties properties;
    public CustomerAccess(CustomerAccessProperties properties) { this.properties = properties; }
    public AuthorizedCustomer require(Authentication authentication, Role role) {
        try {
            var actor=new com.cognizant.stablecoins.platform.security.VerifiedCustomerAccess(properties).require(authentication,role);
            return new AuthorizedCustomer(actor.customerId(),actor.subject());
        } catch(com.cognizant.stablecoins.platform.security.AccessFailure failure) {
            throw new PaymentAccessException(failure.code(),failure.code());
        }
    }
}
