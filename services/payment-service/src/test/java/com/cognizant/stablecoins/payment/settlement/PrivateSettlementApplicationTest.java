package com.cognizant.stablecoins.payment.settlement;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.flow.BesuPaymentFailureService;
import com.cognizant.stablecoins.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PrivateSettlementApplicationTest {
 final SettlementExecutionClaimServiceTest f=new SettlementExecutionClaimServiceTest();
 final PaymentService payments=mock(PaymentService.class);final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final SettlementQuoteService quotes=mock(SettlementQuoteService.class);final RecommendationService reviews=mock(RecommendationService.class);
 final SettlementExecutionClaimService claims=mock(SettlementExecutionClaimService.class);
 @SuppressWarnings("unchecked") final ObjectProvider<BesuPaymentFailureService> recovery=mock(ObjectProvider.class);
 PrivateSettlementApplication service;
 @BeforeEach void setup(){f.setup();when(f.payments.findById(f.payment.getPaymentId())).thenReturn(Optional.of(f.payment));
  service=new PrivateSettlementApplication(payments,jdbc,quotes,reviews,claims,new ObjectMapper(),new MockEnvironment(),recovery,f.access,f.payments);}
 @Test void foreignPaymentIsRejectedBeforeAnyCrossOwnerReadOrCommand(){
  doThrow(new IllegalArgumentException("DENIED")).when(payments).getPayment(f.payment.getPaymentId(),f.actor);
  assertThrows(IllegalArgumentException.class,()->service.evidence(f.payment.getPaymentId(),f.actor));verifyNoInteractions(jdbc,quotes,reviews,claims,recovery);
 }
 @Test void legacyPublicPaymentDoesNotEnterPrivateQueries(){
  f.payment.setIntent(null);assertThrows(ApprovalConflictException.class,()->service.evidence(f.payment.getPaymentId(),f.actor));verifyNoInteractions(jdbc,recovery);
 }
 @Test void proposalUsesOnlyVerifiedScopeAndServerQuoteOwner(){
  UUID preparation=UUID.randomUUID(),key=UUID.randomUUID();service.propose(f.payment.getPaymentId(),preparation,key,f.actor);
  var order=inOrder(payments,quotes,reviews);order.verify(payments).getPayment(f.payment.getPaymentId(),f.actor);
  order.verify(quotes).prepare(f.payment.getPaymentId(),preparation,key,f.actor);order.verify(reviews).proposeSettlement(f.payment.getPaymentId(),preparation,key,f.actor);
 }
 @Test void executionKeyAndExactApprovedQuoteAreDelegatedWithoutFundingOrSubmission(){
  UUID key=UUID.randomUUID(),review=UUID.randomUUID(),quote=UUID.randomUUID();
  service.claim(f.payment.getPaymentId(),review,key,2,quote,1,f.actor);
  verify(claims).claim(f.payment.getPaymentId(),key,review,2,quote,1,f.actor);verifyNoInteractions(jdbc,quotes,reviews,recovery);
 }
 @Test void projectionContainsNoSignedBytesSecretsOrInferredPublicEvidence(){
  var result=service.evidence(f.payment.getPaymentId(),f.actor);
  assertEquals("PRIVATE_DEMO",result.get("classification"));assertEquals(false,result.get("newPaymentsEnabled"));
  assertEquals(Map.of("eligible",false,"reason","NO_UNAUTHORIZED_RECOVERY_PENDING"),result.get("recovery"));
  var queries=org.mockito.ArgumentCaptor.forClass(String.class);
  verify(jdbc,atLeastOnce()).query(queries.capture(),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),eq(f.payment.getPaymentId()));
  assertTrue(queries.getAllValues().stream().noneMatch(s->s.contains("signed_payload")||s.contains("select *")||s.contains("password")||s.contains("keystore")));
 }
 @Test void foreignExecutionIdIsRejectedBeforeReadingItsCanonicalDocument(){
  assertThrows(ApprovalConflictException.class,()->service.authorizeRelease(f.payment.getPaymentId(),UUID.randomUUID(),"a".repeat(64),f.actor));
  verify(jdbc).queryForList(anyString(),eq(UUID.class),eq(f.payment.getPaymentId()));verifyNoInteractions(recovery);
 }
}
