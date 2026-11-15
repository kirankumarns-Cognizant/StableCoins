package com.cognizant.stablecoins.platform.security;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
/** Scope alone cannot turn a public application client into an internal financial caller. */
public class InternalServiceAccess {
    private final String clientId;
    private final String scope;
    public InternalServiceAccess(String clientId,String scope){this.clientId=clientId;this.scope=scope;}
    public boolean allowed(Authentication authentication) {
        return clientId!=null && !clientId.isBlank() && authentication instanceof JwtAuthenticationToken jwt
            && jwt.isAuthenticated() && clientId.equals(jwt.getToken().getClaimAsString("client_id"))
            && jwt.getAuthorities().stream().anyMatch(a->("SCOPE_"+scope).equals(a.getAuthority()));
    }
    public void require(Authentication authentication){if(!allowed(authentication)) throw new AccessFailure("INTERNAL_SERVICE_ACCESS_REQUIRED",403);}
}
