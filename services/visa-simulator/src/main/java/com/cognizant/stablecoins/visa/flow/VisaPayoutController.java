package com.cognizant.stablecoins.visa.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.platform.security.*;
import com.cognizant.stablecoins.visa.liquidity.VisaPaymentAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.UUID;
@RestController @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class VisaPayoutController {
 private final VisaPayoutFlow flow;private final VisaPaymentAdapter adapter;private final PaymentOwnership ownership;private final InternalServiceAccess internal;private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Environment env;
 public VisaPayoutController(VisaPayoutFlow flow,VisaPaymentAdapter adapter,PaymentOwnership ownership,InternalServiceAccess internal,JdbcTemplate jdbc,ObjectMapper mapper,Environment env){this.flow=flow;this.adapter=adapter;this.ownership=ownership;this.internal=internal;this.jdbc=jdbc;this.mapper=mapper;this.env=env;}
 public record Funding(String providerId,VisaPaymentAdapter.Currency currency,BigDecimal amount){}
 public record Resolution(Boolean succeeded){}
 @GetMapping("/api/v1/payments/{paymentId}/payout") public LifecycleReadModels.Payout get(@PathVariable String paymentId,Authentication auth){ownership.require(paymentId,auth);return new LifecycleReadModels(jdbc,mapper).payout(paymentId).orElseThrow(()->new AccessFailure("PAYOUT_NOT_FOUND",404));}
 @PostMapping("/visa-simulator/v1/prefund") public VisaPaymentAdapter.Balance prefund(@RequestHeader("Idempotency-Key") UUID key,@RequestBody Funding body,Authentication auth){operations(auth);return adapter.prefund(key.toString(),body.providerId(),body.currency(),body.amount());}
 @PostMapping("/visa-simulator/v1/payouts/{id}/resolution") public LifecycleReadModels.Payout resolve(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@RequestBody Resolution body,Authentication auth){operations(auth);if(body.succeeded()==null)throw new IllegalArgumentException("PAYOUT_OUTCOME_REQUIRED");return flow.resolve(id,key,body.succeeded());}
 private void operations(Authentication auth){internal.require(auth);if(!env.getProperty("visa.simulator.operations-enabled",Boolean.class,false))throw new AccessFailure("SIMULATOR_OPERATIONS_DISABLED",403);}
}
