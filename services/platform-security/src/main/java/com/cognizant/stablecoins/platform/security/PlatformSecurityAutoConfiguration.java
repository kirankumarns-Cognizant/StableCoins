package com.cognizant.stablecoins.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import java.util.Map;

@AutoConfiguration(before=SecurityAutoConfiguration.class,after=JdbcTemplateAutoConfiguration.class)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
public class PlatformSecurityAutoConfiguration {
    @Bean @ConditionalOnMissingBean(CustomerMembershipProperties.class) @ConfigurationProperties("app.access")
    CustomerMembershipProperties customerMembershipProperties(){return new CustomerMembershipProperties();}
    @Bean @ConditionalOnMissingBean
    VerifiedCustomerAccess verifiedCustomerAccess(CustomerMembershipProperties properties){return new VerifiedCustomerAccess(properties);}
    @Bean @ConditionalOnMissingBean
    InternalServiceAccess internalServiceAccess(@Value("${connections.identity.service-client-id:}") String client,
            @Value("${connections.identity.service-scope:stablecoins/internal}") String scope){return new InternalServiceAccess(client,scope);}
    @Bean @ConditionalOnBean(JdbcTemplate.class) @ConditionalOnMissingBean
    PaymentOwnership paymentOwnership(JdbcTemplate jdbc,VerifiedCustomerAccess customers,InternalServiceAccess internal){return new PaymentOwnership(jdbc,customers,internal);}
    @Bean @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder platformJwtDecoder(@Value("${connections.identity.provider:COGNITO}") String provider,
            @Value("${connections.identity.issuer-uri:}") String issuer,@Value("${connections.identity.jwk-set-uri:}") String jwks,
            @Value("${connections.identity.client-id:}") String client,@Value("${connections.identity.audience:}") String audience,
            @Value("${connections.identity.http-timeout-ms:5000}") int timeout,@Value("${connections.identity.service-client-id:}") String internal){
        return CognitoJwtFactory.decoderWithInternal(provider,issuer,jwks,client,audience,timeout,internal);
    }
    @Bean @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain platformSecurity(HttpSecurity http,JwtDecoder decoder,ObjectMapper mapper,InternalServiceAccess internal) throws Exception {
        http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/health").permitAll()
                .requestMatchers("/internal/**","/api/v1/blockchain/readiness").access((authentication,context)->new AuthorizationDecision(internal.allowed(authentication.get())))
                .requestMatchers(HttpMethod.POST,"/api/v1/blockchain/**","/api/v1/journals","/api/v1/journals/**")
                    .access((authentication,context)->new AuthorizationDecision(internal.allowed(authentication.get())))
                .anyRequest().authenticated())
            .oauth2ResourceServer(o->o.jwt(j->j.decoder(decoder)).authenticationEntryPoint((request,response,error)->{
                response.setStatus(401);response.setContentType("application/json");mapper.writeValue(response.getOutputStream(),Map.of("code","AUTHENTICATION_REQUIRED","message","Valid access token required"));
            }))
            .exceptionHandling(e->e.authenticationEntryPoint((request,response,error)->{
                response.setStatus(401);response.setContentType("application/json");mapper.writeValue(response.getOutputStream(),Map.of("code","AUTHENTICATION_REQUIRED","message","Valid access token required"));
            }).accessDeniedHandler((request,response,error)->{
                response.setStatus(403);response.setContentType("application/json");mapper.writeValue(response.getOutputStream(),Map.of("code","ACCESS_DENIED","message","Access denied"));
            }));
        return http.build();
    }
    @Bean PlatformAccessErrors platformAccessErrors(){return new PlatformAccessErrors();}
}
