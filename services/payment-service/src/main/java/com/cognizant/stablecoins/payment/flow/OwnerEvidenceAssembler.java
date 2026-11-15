package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.FlowContract.*;
import com.cognizant.stablecoins.payment.routing.*;
import com.cognizant.stablecoins.payment.domain.PaymentRail;
import com.cognizant.stablecoins.payment.intent.BeneficiaryOutcome;
import com.cognizant.stablecoins.payment.approval.RecommendationDocument;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.routing.RoutingEvidence.*;
/** Converts persisted owner records, never scenario fixtures, into the existing seven-signal contract. */
@Component
public class OwnerEvidenceAssembler {
 private final Environment env;
 public OwnerEvidenceAssembler(Environment env){this.env=env;}
 public record Prepared(AssessmentContext context,Snapshot evidence,RecommendationDocument.Quote quote){}
 public record Captured(AssessmentContext context,Snapshot evidence,List<RecommendationDocument.Quote> quotes){}
 public Captured assemble(Request request,List<Quote> quotes,Treasury treasury){
  if(quotes.isEmpty())throw new IllegalStateException("OWNER_QUOTES_REQUIRED");
  var prepared=quotes.stream().map(q->assemble(request,q,treasury)).toList();
  return new Captured(prepared.getFirst().context(),new Snapshot(request.requestId(),1,
   prepared.stream().flatMap(p->p.evidence().candidates().stream()).toList(),prepared.stream().flatMap(p->p.evidence().observations().stream()).toList()),prepared.stream().map(Prepared::quote).toList());
 }
 public Prepared assemble(Request request,Quote q,Treasury t){
  boolean bank=!"USDC_WALLET".equals(request.outcome());
  if(bank&&(q.payoutAmount()==null||!request.outcome().endsWith(q.payoutCurrency())))throw new IllegalStateException("PAYOUT_QUOTE_REQUIRED");
  var payout=bank?Map.of(BeneficiaryOutcome.valueOf(request.outcome()),q.payoutAmount()):Map.<BeneficiaryOutcome,BigDecimal>of();
  if(!request.requestId().equals(q.requestId())||!request.providerIds().contains(q.providerId())||q.sourceUsd().compareTo(request.sourceUsd())!=0||q.transferUsdc().compareTo(request.transferUsdc())!=0)throw new IllegalStateException("OWNER_QUOTE_BINDING_MISMATCH");
  var network=new Network(t.network(),t.chainId(),t.tokenContract(),true);String id="STABLECOIN-"+q.providerId();
  var candidate=new Candidate(id,q.providerId(),bank?PaymentRail.STABLECOIN_PLUS_VISA:PaymentRail.STABLECOIN,BeneficiaryOutcome.valueOf(request.outcome()),network);
  var context=new AssessmentContext(request.paymentId(),request.correlationId(),request.customerId(),request.corridor(),request.sourceUsd(),request.transferUsdc(),t.requiredGasEth(),payout,PaymentRail.valueOf(request.requestedRail()));
  var expiry=q.expiresAt().isBefore(t.expiresAt())?q.expiresAt():t.expiresAt();
  var rate=new BigDecimal(env.getRequiredProperty("app.flow.simulation.eth-usd-budget"));if(rate.signum()<=0)throw new IllegalStateException("ETH_BUDGET_RATE_REQUIRED");
  BigDecimal gasUsd=t.requiredGasEth().multiply(rate).setScale(2,RoundingMode.HALF_EVEN);
  List<CostItem> costs=new ArrayList<>();
  for(var component:FeeComponent.values())costs.add(component==FeeComponent.GAS
   ?new CostItem(component,t.requiredGasEth(),Asset.ETH,gasUsd,new ConversionQuote("SIMULATED-BUDGET-"+q.quoteId(),Asset.ETH,rate,q.createdAt(),expiry,true))
   :new CostItem(component,component==FeeComponent.PLATFORM?q.feeUsd():BigDecimal.ZERO,Asset.USD,component==FeeComponent.PLATFORM?q.feeUsd():BigDecimal.ZERO,null));
  var total=q.feeUsd().add(gasUsd);
  var available=q.available()?Availability.AVAILABLE:Availability.UNAVAILABLE;
  var chainAvailable=t.reasons().isEmpty()?Availability.AVAILABLE:Availability.UNAVAILABLE;
  List<ExternalObservation> external=Arrays.stream(ExternalKind.values()).map(kind->new ExternalObservation(kind,
   switch(kind){case NETWORK_CONGESTION->ExternalUnit.MILLISECONDS;case STABLECOIN_PRICE_DEVIATION->ExternalUnit.BASIS_POINTS;default->ExternalUnit.FLAG;},
   BigDecimal.ZERO,ExternalState.NORMAL,"CONFIGURED-SIMULATION","compliance_flow_quote/"+q.requestId()+"/"+q.providerId()+"/"+kind,q.createdAt(),expiry)).toList();
  var pockets=new ArrayList<Pocket>(List.of(new Pocket(PocketRole.TREASURY_USDC,Asset.USDC,t.totalUsdc(),t.reservedUsdc(),t.availableUsdc()),new Pocket(PocketRole.GAS_ETH,Asset.ETH,t.totalEth(),t.reservedEth(),t.availableEth())));
  if(bank)pockets.add(new Pocket(PocketRole.PROVIDER_FIAT,Asset.valueOf(q.payoutCurrency()),q.providerFiatAvailable(),BigDecimal.ZERO,q.providerFiatAvailable()));
  List<SignalValue> values=List.of(new SuccessRate(q.completed(),q.attempts(),Duration.ofHours(24),DeliveryScope.END_TO_END),
   new Latency(q.latencyMillis(),95,q.attempts(),Duration.ofHours(24),DeliveryScope.END_TO_END),new TransactionCost(costs,total,CostBasis.SIMULATED_DELIVERY_ESTIMATE),
   new FraudRisk(q.fraudScore(),q.simulationVersion(),"compliance_flow_quote/"+q.requestId()+"/"+q.providerId()),
   new RailAvailability(chainAvailable,chainAvailable,available,Availability.AVAILABLE),
   new Liquidity(pockets),new ExternalData(external));
  List<Observation> observations=new ArrayList<>();
  for(var value:values){
   boolean chain=value instanceof Liquidity;var observed=chain?(bank&&q.createdAt().isBefore(t.observedAt())?q.createdAt():t.observedAt()):q.createdAt();
   Unit unit=switch(value.type()){case SUCCESS_RATE->Unit.RATIO;case LATENCY->Unit.MILLISECONDS;case TRANSACTION_COST->Unit.USD;case FRAUD_RISK->Unit.SCORE_0_100;case RAIL_AVAILABILITY->Unit.STATUS;case LIQUIDITY->Unit.ASSET_AMOUNTS;case EXTERNAL_DATA->Unit.TYPED_EXTERNAL;};
   observations.add(new Observation(request.requestId()+"/"+q.providerId()+"/"+value.type(),1,value.type(),id,new Scope(request.customerId(),request.corridor(),request.sourceUsd()),
    chain?(bank?"TREASURY-AND-SIMULATED-PROVIDER":"TREASURY-ADAPTER"):"CONFIGURED-SIMULATION",(chain?"blockchain_flow_observation/":"compliance_flow_quote/")+request.requestId()+(chain?(bank?";visa_liquidity_pocket/"+q.providerId()+":"+q.payoutCurrency():""):"/"+q.providerId()),observed,observed,expiry,unit,
    chain&&!bank?EvidenceMode.valueOf(t.evidenceMode()):EvidenceMode.SIMULATED,Quality.VALID,network,value));
  }
  return new Prepared(context,new Snapshot(request.requestId(),1,List.of(candidate),observations),new RecommendationDocument.Quote(q.quoteId(),q.version(),request.paymentId(),request.correlationId(),request.customerId(),id,q.providerId(),request.sourceUsd(),request.transferUsdc(),payout,total,q.createdAt(),expiry,true));
 }
}
