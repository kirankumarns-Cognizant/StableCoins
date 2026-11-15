package com.cognizant.stablecoins.compliance.flow;
import com.cognizant.stablecoins.flow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.*;
import org.springframework.mock.env.MockEnvironment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class FlowQuoteConsumerTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 final Request request=new Request(UUID.randomUUID(),"PAY-1","CORR-1",UUID.randomUUID(),new BigDecimal("10000"),new BigDecimal("10000"),"0x"+"1".repeat(40),"USDC_WALLET","AUTO","USD-USDC-WALLET",Instant.now().plusSeconds(600),Set.of("SIMULATED-PROVIDER-A","SIMULATED-PROVIDER-B"));
 final MockEnvironment env=new MockEnvironment().withProperty("app.flow.simulation.provider-ids","SIMULATED-PROVIDER-A,SIMULATED-PROVIDER-B").withProperty("app.flow.simulation.quote-ttl","PT5M").withProperty("app.flow.simulation.version","simulation-v1").withProperty("app.flow.simulation.fee-usd","2.00").withProperty("app.flow.simulation.completed","990").withProperty("app.flow.simulation.attempts","1000").withProperty("app.flow.simulation.latency-ms","120000").withProperty("app.flow.simulation.fraud-score","10").withProperty("app.flow.simulation.available","true").withProperty("app.flow.simulation.providers.SIMULATED-PROVIDER-B.fee-usd","5.00").withProperty("app.flow.simulation.providers.SIMULATED-PROVIDER-B.latency-ms","60000");
 FlowQuoteConsumer consumer;
 String body(){return FlowJson.json(mapper,new FlowJson.Envelope(UUID.randomUUID().toString(),"RoutingEvidenceRequested","1.0",request.paymentId(),request.correlationId(),Instant.now(),mapper.valueToTree(request)));}
 @SuppressWarnings("unchecked") @BeforeEach void setup(){consumer=new FlowQuoteConsumer(jdbc,mapper,env);doReturn(List.of(request)).when(jdbc).query(contains("from payment_flow_preparation"),any(RowMapper.class),eq(request.requestId()),eq(request.paymentId()),eq(request.correlationId()));doReturn(List.of(true)).when(jdbc).query(contains("join account"),any(RowMapper.class),eq(request.paymentId()));}
 @Test void persistsConfiguredComparableProviderQuotes(){consumer.prepare(body());var documents=ArgumentCaptor.forClass(String.class);verify(jdbc,times(2)).update(contains("insert into compliance_flow_quote"),eq(request.requestId()),anyString(),anyString(),documents.capture());var a=FlowJson.value(mapper,documents.getAllValues().get(0),Quote.class);var b=FlowJson.value(mapper,documents.getAllValues().get(1),Quote.class);assertThat(a.providerId()).isEqualTo("SIMULATED-PROVIDER-A");assertThat(a.feeUsd()).isEqualByComparingTo("2");assertThat(b.feeUsd()).isEqualByComparingTo("5");assertThat(b.latencyMillis()).isLessThan(a.latencyMillis());assertThat(a.simulationVersion()).isEqualTo("simulation-v1");assertThat(a.quoteId()).isNotEqualTo(b.quoteId());}
 @Test void redeliveryDoesNotRepriceImmutableQuotes(){when(jdbc.queryForList(contains("select request_fingerprint"),eq(String.class),eq(request.requestId()))).thenReturn(List.of(FlowJson.fingerprint(mapper,request)));env.setProperty("app.flow.simulation.fee-usd","99");consumer.prepare(body());verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @SuppressWarnings("unchecked") @Test void untrustedRequestCannotCreateQuotes(){doReturn(List.of()).when(jdbc).query(contains("from payment_flow_preparation"),any(RowMapper.class),eq(request.requestId()),eq(request.paymentId()),eq(request.correlationId()));assertThatThrownBy(()->consumer.prepare(body())).hasMessage("CANONICAL_PREPARATION_REQUIRED");verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @Test void disabledExecutionCannotValidateAClaim(){assertThatThrownBy(()->consumer.validate("{} ")).hasMessage("EXECUTION_DISABLED");verifyNoInteractions(jdbc);}
}
