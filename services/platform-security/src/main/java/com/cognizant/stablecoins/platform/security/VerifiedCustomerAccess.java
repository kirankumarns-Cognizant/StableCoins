package com.cognizant.stablecoins.platform.security;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.UUID;

/** Trusted subject comes only from the verified JWT; membership/role come only from server configuration. */
public class VerifiedCustomerAccess {
    public record Actor(UUID customerId,String subject) {}
    private final CustomerMembershipProperties properties;
    public VerifiedCustomerAccess(CustomerMembershipProperties properties){this.properties=properties;}
    public Actor require(Authentication authentication,CustomerMembershipProperties.Role role) {
        if(!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) throw new AccessFailure("AUTHENTICATION_REQUIRED",401);
        var membership=properties.getMemberships().get(jwt.getToken().getSubject());
        if(membership==null || !membership.roles().contains(role)) throw new AccessFailure("CUSTOMER_ACCESS_DENIED",403);
        return new Actor(membership.customerId(),jwt.getToken().getSubject());
    }
}
