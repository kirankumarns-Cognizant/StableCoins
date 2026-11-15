package com.cognizant.stablecoins.flow;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
class FlowContractTest {
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 Request request(){return new Request(UUID.randomUUID(),"PAY-1","CORR-1",UUID.randomUUID(),new BigDecimal("10000.00"),new BigDecimal("10000.000000"),"0x"+"1".repeat(40),"USDC_WALLET","AUTO","USD-USDC-WALLET",Instant.now().plusSeconds(300),Set.of("SIMULATED-PROVIDER-A"));}
 String envelope(){return FlowJson.json(mapper,new FlowJson.Envelope(UUID.randomUUID().toString(),"RoutingEvidenceRequested","1.0","PAY-1","CORR-1",Instant.now(),mapper.valueToTree(request())));}
 @Test void roundTripPreservesCanonicalAmountsAndProviderOrdering(){var r=request();assertThat(FlowJson.value(mapper,FlowJson.json(mapper,r),Request.class)).isEqualTo(r);}
 @Test void envelopeRetainsIdentityAndTypedPayload(){var e=FlowJson.read(mapper,envelope(),"RoutingEvidenceRequested");assertThat(e.paymentId()).isEqualTo("PAY-1");assertThat(FlowJson.convert(mapper,e.payload(),Request.class).sourceUsd()).isEqualByComparingTo("10000");}
 @Test void rejectsDuplicateJsonKeys(){assertThatThrownBy(()->FlowJson.read(mapper,envelope().replace("\"eventType\":","\"eventType\":\"wrong\",\"eventType\":"),"RoutingEvidenceRequested")).hasMessage("INVALID_FLOW_EVENT");}
 @Test void rejectsWrongVersion(){assertThatThrownBy(()->FlowJson.read(mapper,envelope().replace("1.0","2.0"),"RoutingEvidenceRequested")).hasMessage("INVALID_FLOW_EVENT");}
 @Test void rejectsWrongCommand(){assertThatThrownBy(()->FlowJson.read(mapper,envelope(),"BlockchainSubmissionRequested")).hasMessage("INVALID_FLOW_EVENT");}
 @Test void rejectsTrailingDocument(){assertThatThrownBy(()->FlowJson.read(mapper,envelope()+"{}","RoutingEvidenceRequested")).hasMessage("INVALID_FLOW_EVENT");}
 @Test void rejectsOversizedInput(){assertThatThrownBy(()->FlowJson.read(mapper,"x".repeat(131073),"RoutingEvidenceRequested")).hasMessage("INVALID_FLOW_EVENT");}
 @Test void rejectsFractionalCentsAndTokenPrecision(){assertThatThrownBy(()->money(new BigDecimal("1.001"),2)).hasMessage("INVALID_FLOW_AMOUNT");assertThatThrownBy(()->money(new BigDecimal("1.0000001"),6)).hasMessage("INVALID_FLOW_AMOUNT");}
 @Test void rejectsNonPositiveAndOversizedMoney(){for(var amount:List.of("0","-1","1000000000000"))assertThatThrownBy(()->money(new BigDecimal(amount),2)).hasMessage("INVALID_FLOW_AMOUNT");}
 @Test void doesNotInventAnFxRate(){var r=request();assertThatThrownBy(()->new Request(r.requestId(),r.paymentId(),r.correlationId(),r.customerId(),r.sourceUsd(),new BigDecimal("9999"),r.recipientWallet(),r.outcome(),r.requestedRail(),r.corridor(),r.deadline(),r.providerIds())).hasMessage("ONLY_NOTIONAL_ONE_TO_ONE_SUPPORTED");}
 @Test void rejectsZeroWallet(){var r=request();assertThatThrownBy(()->new Request(r.requestId(),r.paymentId(),r.correlationId(),r.customerId(),r.sourceUsd(),r.transferUsdc(),"0x"+"0".repeat(40),r.outcome(),r.requestedRail(),r.corridor(),r.deadline(),r.providerIds())).hasMessage("INVALID_RECIPIENT");}
 @org.junit.jupiter.api.Test void fractionalSecondsAndMoneySurviveTheDomainEnvelopeExactly(){
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
  var deadline=java.time.Instant.parse("2030-01-01T00:00:00.123456789Z");
  var request=new FlowContract.Request(java.util.UUID.randomUUID(),"PAY-EXACT","CORR-EXACT",java.util.UUID.randomUUID(),new java.math.BigDecimal("10000.01"),new java.math.BigDecimal("10000.01"),"0x"+"1".repeat(40),"USDC_WALLET","AUTO","DEMO",deadline,java.util.Set.of("A"));
  var body=FlowJson.json(mapper,new FlowJson.Envelope(java.util.UUID.randomUUID().toString(),"RoutingEvidenceRequested","1.0",request.paymentId(),request.correlationId(),java.time.Instant.now(),mapper.valueToTree(request)));
  var parsed=FlowJson.convert(mapper,FlowJson.read(mapper,body,"RoutingEvidenceRequested").payload(),FlowContract.Request.class);
  org.assertj.core.api.Assertions.assertThat(parsed).isEqualTo(request);
 }
}
