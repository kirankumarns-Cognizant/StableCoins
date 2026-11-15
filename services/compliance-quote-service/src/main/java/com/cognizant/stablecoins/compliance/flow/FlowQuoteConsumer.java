package com.cognizant.stablecoins.compliance.flow;
import com.cognizant.stablecoins.flow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
/** Configured simulation, never a live FX, screening or provider performance claim. */
@Component @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class FlowQuoteConsumer {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Environment env;private final FlowReadModels reads;
 public FlowQuoteConsumer(JdbcTemplate jdbc,ObjectMapper mapper,Environment env){this.jdbc=jdbc;this.mapper=mapper;this.env=env;this.reads=new FlowReadModels(jdbc,mapper);}
 @SqsListener("${app.sqs.flow-quote-queue-name}") @Transactional
 public void prepare(String body){
  var event=FlowJson.read(mapper,body,"RoutingEvidenceRequested");var request=FlowJson.convert(mapper,event.payload(),Request.class);
  bind(event,request);reads.requireRequested(request);
  String fingerprint=FlowJson.fingerprint(mapper,request);
  var existing=jdbc.queryForList("select request_fingerprint from compliance_flow_quote where request_id=?",String.class,request.requestId());
  if(!existing.isEmpty()){if(existing.stream().anyMatch(value->!fingerprint.equals(value)))throw new IllegalStateException("QUOTE_REQUEST_CONFLICT");return;}
  var selected=providers().stream().filter(request.providerIds()::contains).sorted().toList();
  if(selected.isEmpty())throw new IllegalStateException("NO_CONFIGURED_SIMULATED_PROVIDER");
  var now=Instant.now();var expiry=now.plus(Duration.parse(env.getRequiredProperty("app.flow.simulation.quote-ttl")));
  if(expiry.isAfter(request.deadline()))expiry=request.deadline();
  String currency=request.outcome().equals("USDC_WALLET")?null:request.outcome().endsWith("INR")?"INR":"USD";
  BigDecimal rate=currency==null?null:new BigDecimal(env.getRequiredProperty("app.flow.simulation.fx-"+currency.toLowerCase()));
  for(String provider:selected){
   var quote=new Quote(request.requestId(),UUID.nameUUIDFromBytes((request.requestId()+"/quote/"+provider).getBytes(java.nio.charset.StandardCharsets.UTF_8)),1,provider,
    env.getRequiredProperty("app.flow.simulation.version"),request.sourceUsd(),request.transferUsdc(),decimal(provider,"fee-usd"),
    Long.parseLong(setting(provider,"completed")),Long.parseLong(setting(provider,"attempts")),Long.parseLong(setting(provider,"latency-ms")),decimal(provider,"fraud-score"),Boolean.parseBoolean(setting(provider,"available")),now,expiry,currency,currency==null?null:request.sourceUsd().multiply(rate).setScale(2,java.math.RoundingMode.HALF_EVEN),rate,currency==null?null:reads.providerAvailable(provider,currency));
   jdbc.update("insert into compliance_flow_quote(request_id,provider_id,request_fingerprint,status,document) values (?,?,?,'READY',cast(? as jsonb)) on conflict(request_id,provider_id) do nothing",request.requestId(),provider,fingerprint,FlowJson.json(mapper,quote));
  }
 }
 @SqsListener("${app.sqs.flow-validation-queue-name}") @Transactional
 public void validate(String body){
  if(!env.getProperty("app.flow.execution-enabled",Boolean.class,false))throw new IllegalStateException("EXECUTION_DISABLED");
  var event=FlowJson.read(mapper,body,"StablecoinExecutionRequested");var command=FlowJson.convert(mapper,event.payload(),Command.class);
  var execution=reads.execution(command.executionId());bind(event,execution.request());if(reads.validated(execution.executionId()))return;reads.requireApproved(execution);
  var quote=reads.quote(execution.preparationId()).orElseThrow(()->new IllegalStateException("QUOTE_REQUIRED")).document();
  if(!quote.expiresAt().isAfter(Instant.now())||!quote.available()||!Boolean.parseBoolean(setting(quote.providerId(),"available"))||quote.fraudScore().compareTo(decimal(quote.providerId(),"fraud-score"))!=0||quote.latencyMillis()!=Long.parseLong(setting(quote.providerId(),"latency-ms"))||quote.completed()!=Long.parseLong(setting(quote.providerId(),"completed"))||quote.attempts()!=Long.parseLong(setting(quote.providerId(),"attempts"))||!providers().contains(quote.providerId())||!quote.simulationVersion().equals(env.getRequiredProperty("app.flow.simulation.version"))
    ||quote.feeUsd().compareTo(decimal(quote.providerId(),"fee-usd"))!=0)throw new IllegalStateException("QUOTE_REVALIDATION_REQUIRED");
  if(quote.payoutCurrency()!=null&&(quote.fxRate().compareTo(new BigDecimal(env.getRequiredProperty("app.flow.simulation.fx-"+quote.payoutCurrency().toLowerCase())))!=0||reads.providerAvailable(quote.providerId(),quote.payoutCurrency()).compareTo(quote.payoutAmount())<0))throw new IllegalStateException("PAYOUT_QUOTE_REVALIDATION_REQUIRED");
  jdbc.update("insert into compliance_execution_validation(execution_id,status,quote_id) values (?,'VALIDATED',?) on conflict(execution_id) do nothing",execution.executionId(),quote.quoteId());
 }
 private String setting(String provider,String name){return env.getProperty("app.flow.simulation.providers."+provider+"."+name,env.getRequiredProperty("app.flow.simulation."+name));}
 private BigDecimal decimal(String provider,String name){return new BigDecimal(setting(provider,name));}
 private Set<String> providers(){return Set.copyOf(Arrays.asList(env.getRequiredProperty("app.flow.simulation.provider-ids").split(",")));}
 private static void bind(FlowJson.Envelope event,Request request){if(!event.paymentId().equals(request.paymentId())||!event.correlationId().equals(request.correlationId()))throw new IllegalArgumentException("FLOW_ENVELOPE_MISMATCH");}
}
