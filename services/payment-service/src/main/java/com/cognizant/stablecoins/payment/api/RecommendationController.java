package com.cognizant.stablecoins.payment.api;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.security.CustomerAccess;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.*;
@RestController @RequestMapping("/api/v1/payments/{paymentId}/recommendations/{id}")
public class RecommendationController {
    private final RecommendationService service;
    private final CustomerAccess access;
    public RecommendationController(RecommendationService service,CustomerAccess access) { this.service=service; this.access=access; }
    public record Acceptance(@Min(1) int expectedVersion,@NotNull UUID quoteId,@Min(1) int quoteVersion) { }
    public record Rejection(@Min(1) int expectedVersion) { }
    @GetMapping
    public RecommendationView get(@PathVariable String paymentId,@PathVariable UUID id,Authentication auth) {
        return service.get(paymentId,id,access.require(auth,PAYMENT_OPERATOR));
    }
    @PostMapping("/quote-acceptance")
    public RecommendationView acceptQuote(@PathVariable String paymentId,@PathVariable UUID id,
            @Valid @RequestBody Acceptance body,Authentication auth) {
        return service.acceptQuote(paymentId,id,body.expectedVersion(),body.quoteId(),body.quoteVersion(),access.require(auth,PAYMENT_APPROVER));
    }
    @PostMapping("/approvals")
    public RecommendationView approve(@PathVariable String paymentId,@PathVariable UUID id,
            @Valid @RequestBody Acceptance body,Authentication auth) {
        return service.approve(paymentId,id,body.expectedVersion(),body.quoteId(),body.quoteVersion(),access.require(auth,PAYMENT_APPROVER));
    }
    @PostMapping("/rejection")
    public RecommendationView reject(@PathVariable String paymentId,@PathVariable UUID id,
            @Valid @RequestBody Rejection body,Authentication auth) {
        return service.reject(paymentId,id,body.expectedVersion(),access.require(auth,PAYMENT_APPROVER));
    }
}
