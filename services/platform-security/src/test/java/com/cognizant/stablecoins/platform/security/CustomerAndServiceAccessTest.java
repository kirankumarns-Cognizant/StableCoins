package com.cognizant.stablecoins.platform.security;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CustomerAndServiceAccessTest {
 final UUID owner=UUID.randomUUID();
 final CustomerMembershipProperties properties=new CustomerMembershipProperties();
 final InternalServiceAccess internal=new InternalServiceAccess("serviceClient","stablecoins/internal");
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 CustomerAndServiceAccessTest(){properties.setMemberships(Map.of("operator",new CustomerMembershipProperties.Membership(owner,Set.of(CustomerMembershipProperties.Role.PAYMENT_OPERATOR))));}
 static JwtAuthenticationToken token(String subject,String client,boolean scope){
  var jwt=Jwt.withTokenValue("test-token").header("alg","RS256").subject(subject).claim("client_id",client).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
  return new JwtAuthenticationToken(jwt,scope?List.of(new SimpleGrantedAuthority("SCOPE_stablecoins/internal")):List.of());
 }
 PaymentOwnership ownership(){return new PaymentOwnership(jdbc,new VerifiedCustomerAccess(properties),internal);}
 void ownerRows(List<UUID> rows){when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),eq("PAY-1"))).thenReturn(rows);}
 @Test void operatorMembershipIsServerOwned(){assertEquals(owner,new VerifiedCustomerAccess(properties).require(token("operator","browserClient",false),CustomerMembershipProperties.Role.PAYMENT_OPERATOR).customerId());}
 @Test void missingMembershipIsDenied(){assertEquals(403,assertThrows(AccessFailure.class,()->new VerifiedCustomerAccess(properties).require(token("stranger","browserClient",true),CustomerMembershipProperties.Role.PAYMENT_OPERATOR)).status());}
 @Test void operatorDoesNotAcquireApproverRole(){assertThrows(AccessFailure.class,()->new VerifiedCustomerAccess(properties).require(token("operator","browserClient",false),CustomerMembershipProperties.Role.PAYMENT_APPROVER));}
 @Test void missingAuthenticationIsDenied(){assertEquals(401,assertThrows(AccessFailure.class,()->new VerifiedCustomerAccess(properties).require(null,CustomerMembershipProperties.Role.PAYMENT_OPERATOR)).status());}
 @Test void scopeOnBrowserClientCannotGrantServiceAuthority(){assertFalse(internal.allowed(token("operator","browserClient",true)));}
 @Test void internalClientAlsoRequiresScope(){assertFalse(internal.allowed(token("worker","serviceClient",false)));assertTrue(internal.allowed(token("worker","serviceClient",true)));}
 @Test void unconfiguredInternalClientIsClosed(){assertFalse(new InternalServiceAccess("","stablecoins/internal").allowed(token("worker","serviceClient",true)));}
 @Test void ownedPaymentUsesParameterizedRead(){ownerRows(List.of(owner));assertDoesNotThrow(()->ownership().require("PAY-1",token("operator","browserClient",false)));verify(jdbc).query(contains("where p.payment_id=?"),org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),eq("PAY-1"));}
 @Test void foreignPaymentIsIndistinguishableFromMissing(){ownerRows(List.of(UUID.randomUUID()));assertEquals("PAYMENT_NOT_FOUND",assertThrows(AccessFailure.class,()->ownership().require("PAY-1",token("operator","browserClient",false))).code());ownerRows(List.of());assertEquals(404,assertThrows(AccessFailure.class,()->ownership().require("PAY-1",token("operator","browserClient",false))).status());}
 @Test void unavailableOwnershipStoreFailsClosed(){when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),eq("PAY-1"))).thenThrow(new DataAccessResourceFailureException("private connection"));assertEquals("EVIDENCE_STORE_UNAVAILABLE",assertThrows(AccessFailure.class,()->ownership().require("PAY-1",token("operator","browserClient",false))).code());}
 @Test void internalScopedCallerDoesNotNeedCustomerMembership(){assertDoesNotThrow(()->ownership().require("PAY-1",token("worker","serviceClient",true)));verifyNoInteractions(jdbc);}
 @Test void malformedPaymentNeverReachesSql(){assertEquals(404,assertThrows(AccessFailure.class,()->ownership().require("invalid / payment",token("operator","browserClient",false))).status());verifyNoInteractions(jdbc);}
 @Test void missingIdentityDecoderRejectsEveryToken(){var decoder=CognitoJwtFactory.decoder("COGNITO","","","","",5000);assertThrows(org.springframework.security.oauth2.jwt.BadJwtException.class,()->decoder.decode("anything"));}
 @Test void internalClientMustDifferFromPublicClient(){assertThrows(IllegalArgumentException.class,()->CognitoJwtFactory.decoderWithInternal("COGNITO","https://id.test/pool","https://id.test/jwks","sameClient","",5000,"sameClient"));}
}
