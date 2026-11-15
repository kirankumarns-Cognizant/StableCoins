package com.cognizant.stablecoins.payment.api;
import com.cognizant.stablecoins.payment.flow.PaymentFlowService;
import com.cognizant.stablecoins.payment.security.CustomerAccess;
import com.cognizant.stablecoins.payment.approval.ApprovalConflictException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.UUID;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR;
@RestController @RequestMapping("/api/v1/payments/{paymentId}")
@ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class PaymentFlowController {
 private final PaymentFlowService flow;private final CustomerAccess access;
 public PaymentFlowController(PaymentFlowService flow,CustomerAccess access){this.flow=flow;this.access=access;}
 @PostMapping("/recommendation-preparations") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
 public PaymentFlowService.Preparation prepare(@PathVariable String paymentId,@RequestHeader("Idempotency-Key") String key,Authentication auth){return flow.prepare(paymentId,key(key),access.require(auth,PAYMENT_OPERATOR));}
 @GetMapping("/recommendation-preparations/{id}")
 public PaymentFlowService.Preparation preparation(@PathVariable String paymentId,@PathVariable UUID id,Authentication auth){return flow.preparation(paymentId,id,access.require(auth,PAYMENT_OPERATOR));}
 @PostMapping("/recommendations/{id}/execution") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
 public PaymentFlowService.ExecutionView execute(@PathVariable String paymentId,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecommendationController.Acceptance body,Authentication auth){return flow.execute(paymentId,id,key(key),body.expectedVersion(),body.quoteId(),body.quoteVersion(),access.require(auth,PAYMENT_OPERATOR));}
 @GetMapping("/execution")
 public PaymentFlowService.ExecutionView execution(@PathVariable String paymentId,Authentication auth){return flow.execution(paymentId,access.require(auth,PAYMENT_OPERATOR));}
 @GetMapping("/closure")
 public PaymentFlowService.ClosureView closure(@PathVariable String paymentId,Authentication auth){return flow.closure(paymentId,access.require(auth,PAYMENT_OPERATOR));}
 private UUID key(String value){try{UUID id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw new IllegalArgumentException();return id;}catch(IllegalArgumentException error){throw new ApprovalConflictException("UUID_IDEMPOTENCY_KEY_REQUIRED");}}
}
