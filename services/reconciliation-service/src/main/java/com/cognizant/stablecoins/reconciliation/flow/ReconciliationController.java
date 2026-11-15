package com.cognizant.stablecoins.reconciliation.flow;
import com.cognizant.stablecoins.platform.security.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
@RestController @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
@RequestMapping("/api/v1/payments/{paymentId}")
public class ReconciliationController {
 private final ReconciliationService service;private final PaymentOwnership ownership;private final InternalServiceAccess internal;private final JdbcTemplate jdbc;
 public ReconciliationController(ReconciliationService service,PaymentOwnership ownership,InternalServiceAccess internal,JdbcTemplate jdbc){this.service=service;this.ownership=ownership;this.internal=internal;this.jdbc=jdbc;}
 @GetMapping("/reconciliation") public ReconciliationService.Report latest(@PathVariable String paymentId,Authentication auth){ownership.require(paymentId,auth);return service.latest(paymentId).orElseThrow(()->new AccessFailure("RECONCILIATION_NOT_FOUND",404));}
 @PostMapping("/reconciliation-runs") public ReconciliationService.Report run(@PathVariable String paymentId,@RequestHeader("Idempotency-Key") UUID key,Authentication auth){
  internal.require(auth);ownership.require(paymentId,auth);
  UUID id=jdbc.queryForList("select execution_id from payment_execution where payment_id=?",UUID.class,paymentId).stream().findFirst().orElseThrow(()->new AccessFailure("EXECUTION_NOT_FOUND",404));return service.run(key,id);
 }
}
