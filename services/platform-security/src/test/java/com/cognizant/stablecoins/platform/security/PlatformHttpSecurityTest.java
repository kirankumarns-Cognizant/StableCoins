package com.cognizant.stablecoins.platform.security;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(classes=PlatformHttpSecurityTest.App.class,properties={"connections.identity.service-client-id=serviceClient","connections.identity.service-scope=stablecoins/internal"})
@AutoConfigureMockMvc
class PlatformHttpSecurityTest {
 @Configuration @EnableAutoConfiguration(exclude=DataSourceAutoConfiguration.class)
 @Import(Echo.class)
 static class App {
  @Bean JwtDecoder testDecoder(){return token->{
   if(!java.util.Set.of("public","publicScoped","internal","internalUnscoped").contains(token))throw new BadJwtException("Rejected");
   return Jwt.withTokenValue(token).header("alg","RS256").subject("actor").claim("client_id",token.startsWith("internal")?"serviceClient":"browserClient")
    .claim("scope",token.equals("internal")||token.equals("publicScoped")?"stablecoins/internal":"openid")
    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();};}
 }
 @RestController static class Echo {
  @GetMapping({"/health","/api/v1/evidence","/api/v1/blockchain/readiness"}) String get(){return "ok";}
  @PostMapping({"/internal/command","/api/v1/blockchain/transfers","/api/v1/journals"}) String post(){return "ok";}
 }
 @Autowired MockMvc mvc;
 @Test void healthIsPublic()throws Exception{mvc.perform(get("/health")).andExpect(status().isOk());}
 @Test void evidenceNeedsAuthentication()throws Exception{mvc.perform(get("/api/v1/evidence")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));}
 @Test void invalidBearerIsRejected()throws Exception{mvc.perform(get("/api/v1/evidence").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());}
 @Test void verifiedCallerPassesAuthenticationBoundary()throws Exception{mvc.perform(get("/api/v1/evidence").header("Authorization","Bearer public")).andExpect(status().isOk());}
 @Test void browserClientCannotExecuteEvenWithScope()throws Exception{for(String path:java.util.List.of("/internal/command","/api/v1/blockchain/transfers","/api/v1/journals"))mvc.perform(post(path).header("Authorization","Bearer publicScoped")).andExpect(status().isForbidden());}
 @Test void internalClientWithoutScopeCannotExecute()throws Exception{mvc.perform(post("/internal/command").header("Authorization","Bearer internalUnscoped")).andExpect(status().isForbidden());}
 @Test void internalScopedClientCanExecute()throws Exception{for(String path:java.util.List.of("/internal/command","/api/v1/blockchain/transfers","/api/v1/journals"))mvc.perform(post(path).header("Authorization","Bearer internal")).andExpect(status().isOk());}
 @Test void readinessIsInternalOnly()throws Exception{mvc.perform(get("/api/v1/blockchain/readiness").header("Authorization","Bearer publicScoped")).andExpect(status().isForbidden());mvc.perform(get("/api/v1/blockchain/readiness").header("Authorization","Bearer internal")).andExpect(status().isOk());}
}
