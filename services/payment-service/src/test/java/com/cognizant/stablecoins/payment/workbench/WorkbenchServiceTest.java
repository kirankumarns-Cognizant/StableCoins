package com.cognizant.stablecoins.payment.workbench;
import com.cognizant.stablecoins.payment.service.PaymentService;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WorkbenchServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final PaymentService payments=mock(PaymentService.class);
 final CustomerAccessProperties access=new CustomerAccessProperties();
 final WorkbenchService views=new WorkbenchService(jdbc,new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()),payments,access,new RoutingPolicyProperties());
 @Test void foreignPaymentIsDeniedBeforeAnyCrossOwnerRead(){var actor=new AuthorizedCustomer(UUID.randomUUID(),"operator");when(payments.getPayment("PAY-FOREIGN",actor)).thenThrow(new IllegalArgumentException("PAYMENT_NOT_FOUND"));assertThrows(IllegalArgumentException.class,()->views.evidence("PAY-FOREIGN",actor));verifyNoInteractions(jdbc);}
 @Test void unconfiguredMembershipCannotReadCatalog(){assertThrows(IllegalStateException.class,()->views.catalog(new AuthorizedCustomer(UUID.randomUUID(),"unmapped")));verifyNoInteractions(jdbc);}
 @Test void membershipCannotBeReboundToAnotherCustomer(){var configured=UUID.randomUUID();access.setMemberships(Map.of("subject",new com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Membership(configured,Set.of(com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR))));assertThrows(IllegalStateException.class,()->views.catalog(new AuthorizedCustomer(UUID.randomUUID(),"subject")));verifyNoInteractions(jdbc);}
 @Test void exactProjectionPreservesOneWeiAndLargeLedgerAmounts(){var tree=views.exact(Map.of("wei",new BigDecimal("9007199254740993"),"eth",new BigDecimal("0.123456789012345678"),"entries",List.of(Map.of("debit",new BigDecimal("999999999999.999999")))));assertTrue(tree.get("wei").isTextual());assertEquals("9007199254740993",tree.get("wei").asText());assertEquals("0.123456789012345678",tree.get("eth").asText());assertEquals("999999999999.999999",tree.get("entries").get(0).get("debit").asText());}

 @Test void catalogProjectsConfirmedDeadlineAndPreservesLegacyPayments()throws Exception{
  assertCatalogDeadline("2026-11-21T16:20:51Z","2026-11-21T16:20:51Z");
 }
 @Test void catalogNormalizesHibernatePersistedInstantToIsoWithoutLosingFractionalSeconds()throws Exception{
  var serializer=new org.hibernate.type.format.jackson.JacksonJsonFormatMapper();
  for(String value:List.of("2026-11-21T16:20:51.155Z","2026-11-21T16:20:51.123456789Z","1970-01-01T00:00:00Z","1969-12-31T23:59:59.500Z")){
   var instant=java.time.Instant.parse(value);
   String stored=serializer.toString(instant,java.time.Instant.class);
   assertTrue(new ObjectMapper().readTree(stored).isNumber(),"Fixture must use Hibernate's real numeric JSON timestamp");
   assertCatalogDeadline(stored,instant.toString());
  }
 }
 private void assertCatalogDeadline(String storedDeadline,String expectedDeadline)throws Exception{
  var customer=UUID.randomUUID();var actor=new AuthorizedCustomer(customer,"operator");
  access.setMemberships(Map.of("operator",new com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Membership(customer,Set.of(com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR))));
  var created=java.sql.Timestamp.from(java.time.Instant.parse("2026-11-20T15:21:14Z"));
  doAnswer(call->{
   String sql=call.getArgument(0);assertEquals(customer,call.getArgument(2));
   if(!sql.contains("from payment p"))return List.of();
   // Exercise the JDBC-to-JSON projection, including an older payment without an intent.
   assertTrue(sql.contains("p.intent_snapshot #>> '{instruction,deliverBy}' as deliver_by"));
   assertTrue(sql.contains("where a.customer_id=? order by p.created_at desc,p.payment_id limit 50"));
   org.springframework.jdbc.core.RowMapper<Map<String,Object>> row=call.getArgument(1);
   var rs=mock(java.sql.ResultSet.class);var meta=mock(java.sql.ResultSetMetaData.class);
   when(rs.getMetaData()).thenReturn(meta);when(meta.getColumnCount()).thenReturn(3);
   when(meta.getColumnLabel(1)).thenReturn("payment_id");when(meta.getColumnLabel(2)).thenReturn("created_at");when(meta.getColumnLabel(3)).thenReturn("deliver_by");
   when(rs.getObject(1)).thenReturn("PAY-NEW");when(rs.getObject(2)).thenReturn(created);when(rs.getObject(3)).thenReturn(storedDeadline);
   var recent=row.mapRow(rs,0);
   when(rs.getObject(1)).thenReturn("PAY-LEGACY");when(rs.getObject(3)).thenReturn(null);
   return List.of(recent,row.mapRow(rs,1));
  }).when(jdbc).query(anyString(),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Map<String,Object>>>any(),any(Object.class));
  var result=new ObjectMapper().valueToTree(views.catalog(actor));
  assertEquals("PAY-NEW",result.at("/payments/0/payment_id").asText());
  assertEquals("2026-11-20T15:21:14Z",result.at("/payments/0/created_at").asText());
  assertEquals(expectedDeadline,result.at("/payments/0/deliver_by").asText());
  assertTrue(result.at("/payments/1/deliver_by").isNull());
 }
}
