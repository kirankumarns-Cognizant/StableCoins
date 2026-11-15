package com.cognizant.stablecoins.flow;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
class FlowAllocationGateTest {
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final Request request=new FlowContractTest().request();final UUID executionId=UUID.randomUUID();
 final Quote quote=new Quote(request.requestId(),UUID.randomUUID(),1,"SIMULATED-PROVIDER-A","v1",request.sourceUsd(),request.transferUsdc(),BigDecimal.ONE,990,1000,120000,BigDecimal.TEN,true,Instant.now(),Instant.now().plusSeconds(120));
 final Execution execution=new Execution(executionId,UUID.randomUUID(),request.requestId(),request.paymentId(),request.correlationId(),"AWAITING_LEDGER",request,UUID.randomUUID(),Instant.now().plusSeconds(120));
 String evidence(UUID conversion,UUID quoteId,String mode){return FlowJson.json(mapper,Map.of("mode",mode,"terms",Map.of("conversionId",conversion,"quoteId",quoteId,"quoteVersion",1,"fundingReference","SIMULATED-"+executionId,"sourceUsd",request.sourceUsd(),"transferUsdc",request.transferUsdc(),"outcome",request.outcome())));}
 @SuppressWarnings("unchecked") void rows(List<String> rows){doReturn(rows).when(jdbc).query(contains("select evidence::text"),any(RowMapper.class),eq(request.paymentId()),eq(request.correlationId()),anyString());}
 @Test void waitsForAllThreePostedLegsAndUsesActualJournalTypes(){rows(List.of(evidence(executionId,quote.quoteId(),"SIMULATED")));assertThat(new FlowReadModels(jdbc,mapper).funded(execution,quote)).isTrue();for(String type:List.of("FUNDING","CONVERSION_USD","CONVERSION_USDC"))verify(jdbc).query(contains("status='POSTED'"),any(RowMapper.class),eq(request.paymentId()),eq(request.correlationId()),eq(type));}
 @Test void missingAllocationIsNotAcknowledged(){rows(List.of());assertThat(new FlowReadModels(jdbc,mapper).funded(execution,quote)).isFalse();}
 @Test void anotherConversionsJournalsCannotAuthorizeSubmission(){rows(List.of(evidence(UUID.randomUUID(),quote.quoteId(),"SIMULATED")));assertThatThrownBy(()->new FlowReadModels(jdbc,mapper).funded(execution,quote)).hasMessage("ALLOCATION_EVIDENCE_MISMATCH");}
 @Test void anotherQuoteCannotAuthorizeSubmission(){rows(List.of(evidence(executionId,UUID.randomUUID(),"SIMULATED")));assertThatThrownBy(()->new FlowReadModels(jdbc,mapper).funded(execution,quote)).hasMessage("ALLOCATION_EVIDENCE_MISMATCH");}
 @Test void simulatedFundingCannotBeRelabeledLive(){rows(List.of(evidence(executionId,quote.quoteId(),"REAL_TESTNET")));assertThatThrownBy(()->new FlowReadModels(jdbc,mapper).funded(execution,quote)).hasMessage("ALLOCATION_EVIDENCE_MISMATCH");}
}
