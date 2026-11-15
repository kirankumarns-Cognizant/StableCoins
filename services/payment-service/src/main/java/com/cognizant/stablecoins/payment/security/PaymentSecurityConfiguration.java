package com.cognizant.stablecoins.payment.security;
import com.cognizant.stablecoins.payment.api.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
@Configuration
public class PaymentSecurityConfiguration {
    @Value("${connections.identity.service-client-id:}") private String internalClientId="";
    @Bean
    JwtDecoder paymentJwtDecoder(@Value("${connections.identity.provider:COGNITO}") String provider,
            @Value("${connections.identity.issuer-uri:}") String issuer,
            @Value("${connections.identity.jwk-set-uri:}") String jwks,
            @Value("${connections.identity.client-id:}") String client,
            @Value("${connections.identity.audience:}") String audience,
            @Value("${connections.identity.http-timeout-ms:5000}") int timeout) {
        return com.cognizant.stablecoins.platform.security.CognitoJwtFactory.decoderWithInternal(provider,issuer,jwks,client,audience,timeout,internalClientId);
    }
    static OAuth2TokenValidator<Jwt> accessTokenValidator(String issuer,String client,String audience) {
        return com.cognizant.stablecoins.platform.security.CognitoJwtFactory.accessTokenValidator(issuer,client,audience);
    }
    @Bean
    SecurityFilterChain paymentSecurity(HttpSecurity http, JwtDecoder paymentJwtDecoder, ObjectMapper mapper) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/health").permitAll().anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(paymentJwtDecoder))
                .authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    mapper.writeValue(response.getOutputStream(), new ApiError("AUTHENTICATION_REQUIRED", "Valid access token required"));
                }))
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, error) -> {
                response.setStatus(401); response.setContentType("application/json");
                mapper.writeValue(response.getOutputStream(), new ApiError("AUTHENTICATION_REQUIRED", "Valid access token required"));
            }).accessDeniedHandler((request, response, error) -> {
                response.setStatus(403); response.setContentType("application/json");
                mapper.writeValue(response.getOutputStream(), new ApiError("CUSTOMER_ACCESS_DENIED", "Access denied"));
            }));
        return http.build();
    }
}
