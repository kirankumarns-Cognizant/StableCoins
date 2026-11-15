package com.cognizant.stablecoins.payment.security;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class AccessTokenValidationTest {
    static KeyPair key,other;
    @BeforeAll static void keys() throws Exception {var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);key=g.generateKeyPair();other=g.generateKeyPair();}
    String signed(String change) throws Exception {
        Instant now=Instant.now();var claims=new JWTClaimsSet.Builder().subject("verified-subject").issuer("https://identity.example/pool")
            .issueTime(Date.from(now.minusSeconds(5))).expirationTime(Date.from(now.plusSeconds(300)))
            .claim("client_id","configured-client").claim("token_use","access").audience("payments");
        switch(change) {
            case "issuer" -> claims.issuer("https://attacker.example"); case "client" -> claims.claim("client_id","other");
            case "id-token" -> claims.claim("token_use","id"); case "audience" -> claims.audience("other");
            case "expired" -> claims.expirationTime(Date.from(now.minusSeconds(300)));
            case "future" -> claims.notBeforeTime(Date.from(now.plusSeconds(300)));
            case "no-expiry" -> claims.expirationTime(null); case "no-subject" -> claims.subject(null);
            default -> { }
        }
        var token=new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),claims.build());
        token.sign(new RSASSASigner((RSAPrivateKey)(change.equals("signature")?other:key).getPrivate()));return token.serialize();
    }
    NimbusJwtDecoder decoder() {var decoder=NimbusJwtDecoder.withPublicKey((RSAPublicKey)key.getPublic()).build();
        decoder.setJwtValidator(PaymentSecurityConfiguration.accessTokenValidator("https://identity.example/pool","configured-client","payments"));return decoder;}
    @Test void verifiesRealRsaSignatureAndAccessClaims() throws Exception {assertThat(decoder().decode(signed("valid")).getSubject()).isEqualTo("verified-subject");}
    @ParameterizedTest @ValueSource(strings={"issuer","client","id-token","audience","expired","future","no-expiry","no-subject","signature"})
    void rejectsInvalidToken(String change) throws Exception {String token=signed(change);assertThatThrownBy(()->decoder().decode(token)).isInstanceOf(JwtException.class);}
    @Test void missingConfigurationDeniesTokensWithoutNetwork() {
        var decoder=new PaymentSecurityConfiguration().paymentJwtDecoder("COGNITO","","","","",5000);
        assertThatThrownBy(()->decoder.decode("token")).isInstanceOf(BadJwtException.class);
    }
    @Test void partialOrInsecureConfigurationFailsStartup() {
        var c=new PaymentSecurityConfiguration();assertThatThrownBy(()->c.paymentJwtDecoder("COGNITO","https://identity.example/pool","","client","",5000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->c.paymentJwtDecoder("COGNITO","http://identity.example/pool","https://identity.example/jwks","client","",5000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->c.paymentJwtDecoder("OTHER","https://identity.example/pool","https://identity.example/jwks","client","",5000)).isInstanceOf(IllegalArgumentException.class);
    }
}
