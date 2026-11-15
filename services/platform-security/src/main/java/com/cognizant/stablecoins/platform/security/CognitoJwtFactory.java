package com.cognizant.stablecoins.platform.security;
import java.net.URI;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** Extracted existing payment-service Cognito validation, reused by each HTTP service. */
public final class CognitoJwtFactory {
    private CognitoJwtFactory() {}
    public static JwtDecoder decoder(String provider,String issuer,String jwks,String client,String audience,int timeout) {
        if (issuer.isBlank() && jwks.isBlank() && client.isBlank() && audience.isBlank())
            return token -> { throw new BadJwtException("Identity is not configured"); };
        if (!"COGNITO".equals(provider) || client.isBlank() || timeout < 100 || timeout > 30000)
            throw new IllegalArgumentException("Cognito identity/client/timeout configuration required");
        requireHttps(issuer); requireHttps(jwks);
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout); requestFactory.setReadTimeout(timeout);
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).restOperations(new RestTemplate(requestFactory)).build();
        decoder.setJwtValidator(accessTokenValidator(issuer, client, audience));
        return decoder;
    }
    public static JwtDecoder decoderWithInternal(String provider,String issuer,String jwks,String client,String audience,int timeout,String internalClient) {
        JwtDecoder decoder=decoder(provider,issuer,jwks,client,audience,timeout);
        if(internalClient!=null && !internalClient.isBlank()) {
            if(!internalClient.matches("[A-Za-z0-9]{1,128}") || internalClient.equals(client))
                throw new IllegalArgumentException("A distinct internal Cognito client is required");
            if(decoder instanceof NimbusJwtDecoder nimbus) {
                var publicValidator=accessTokenValidator(issuer,client,audience);
                var internalValidator=accessTokenValidator(issuer,internalClient,audience);
                nimbus.setJwtValidator(token->{var result=publicValidator.validate(token);return result.hasErrors()?internalValidator.validate(token):result;});
            }
        }
        return decoder;
    }
    public static OAuth2TokenValidator<Jwt> accessTokenValidator(String issuer, String client, String audience) {
        OAuth2TokenValidator<Jwt> access = token -> {
            boolean valid = token.getExpiresAt() != null && token.getIssuedAt() != null
                && !token.getIssuedAt().isAfter(java.time.Instant.now().plusSeconds(60))
                && token.getSubject() != null && !token.getSubject().isBlank() && token.getSubject().length() <= 255
                && "access".equals(token.getClaimAsString("token_use"))
                && client.equals(token.getClaimAsString("client_id"))
                && (audience.isBlank() || token.getAudience()!=null && token.getAudience().contains(audience));
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Invalid access token claims", null));
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), access);
    }
    private static void requireHttps(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null)
            throw new IllegalArgumentException("Identity endpoints must be configured HTTPS URLs");
    }
}
