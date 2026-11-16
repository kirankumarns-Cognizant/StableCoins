package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.flow.FlowContract.*;
import com.cognizant.stablecoins.payment.routing.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import com.cognizant.stablecoins.payment.domain.*;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.support.IntentFixtures;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CapturedPreparationTest {
 @Test void capturedOwnerDocumentsAssembleAndRoundTripWithoutFinancialWrites() throws Exception {
  var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  var data=mapper.readTree(getClass().getResourceAsStream("/flow/captured-preparation.json"));
  var request=FlowJson.value(mapper,data.get("request").toString(),Request.class);
  var quotes=new ArrayList<Quote>();
  for(var node:data.get("quotes"))quotes.add(FlowJson.value(mapper,node.toString(),Quote.class));
  var treasury=FlowJson.value(mapper,data.get("treasury").toString(),Treasury.class);
  var prepared=new OwnerEvidenceAssembler(new MockEnvironment().withProperty("app.flow.simulation.eth-usd-budget","2000")).assemble(request,quotes,treasury);
  var now=Instant.parse("2026-11-16T12:35:56.250Z");
  var instruction=new PaymentIntentRequest(BeneficiaryOutcome.USDC_WALLET,request.deadline(),new BigDecimal("5"),SupportedCurrency.USD,RoutingPriority.LOWEST_COST,Set.of(),true);
  var intent=new PaymentIntentSnapshot(UUID.randomUUID(),1,request.customerId(),"demo-operator",now.minusSeconds(100),SettlementAsset.USDC,true,false,instruction,IntentFixtures.policy("hackathon-a-v1"));
  var rules=new AssessmentRules("test-v1",Duration.ofMinutes(5),Duration.ofHours(24),100,95,new BigDecimal("0.95"),new BigDecimal("30"),new BigDecimal("100"),20,200);
  var result=new DeterministicRoutingAssessmentService(rules,prepared.evidence().candidates().getFirst().network(),Clock.fixed(now,ZoneOffset.UTC)).assess(new AuthorizedCustomer(request.customerId(),"demo-operator"),prepared.context(),intent,prepared.evidence());
  assertThat(result.status()).isEqualTo(AssessmentResult.Status.ASSESSED);
  assertThat(result.preferredCandidateId()).isEqualTo("STABLECOIN-SIMULATED-PROVIDER-A");
  assertThat(prepared.quotes().getFirst().totalFeeUsd()).isEqualByComparingTo("2.21");
  assertThat(prepared.evidence().observations()).hasSize(14);
  var evidenceJson=FlowJson.json(mapper,prepared.evidence());
  assertThat(FlowJson.value(mapper,evidenceJson,RoutingEvidence.Snapshot.class)).isEqualTo(prepared.evidence());
 }
}
