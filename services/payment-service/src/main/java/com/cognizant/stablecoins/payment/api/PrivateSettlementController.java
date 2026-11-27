package com.cognizant.stablecoins.payment.api;

import com.cognizant.stablecoins.payment.settlement.PrivateSettlementApplication;
import com.cognizant.stablecoins.payment.security.CustomerAccess;
import com.cognizant.stablecoins.payment.workbench.WorkbenchService;
import com.cognizant.stablecoins.payment.approval.ApprovalConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.*;

/** Default-off additive API; cannot bypass the hard private instruction acceptance lock. */
@RestController
@RequestMapping("/api/v1/payments/{paymentId}/private-settlement")
@ConditionalOnProperty(name={"app.flow.enabled","app.besu.application-api.enabled"},havingValue="true")
public class PrivateSettlementController {
    private final PrivateSettlementApplication service;private final CustomerAccess access;private final WorkbenchService views;
    public PrivateSettlementController(PrivateSettlementApplication service,CustomerAccess access,WorkbenchService views) {
        this.service=service;this.access=access;this.views=views;
    }
    public record Proposal(@NotNull UUID preparationId) {}
    public record Execution(@NotNull UUID recommendationId,@Min(1) int expectedVersion,@NotNull UUID quoteId,@Min(1) int quoteVersion) {}
    public record Release(@NotNull UUID executionId,@NotNull @Pattern(regexp="[0-9a-f]{64}") String sourceHash) {}
    @GetMapping public Object evidence(@PathVariable String paymentId,Authentication auth) {
        return views.exact(service.evidence(paymentId,access.require(auth,PAYMENT_OPERATOR)));
    }
    @PostMapping("/recommendations") public Object propose(@PathVariable String paymentId,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Proposal body,Authentication auth) {
        var actor=access.require(auth,PAYMENT_OPERATOR);
        return views.exact(service.propose(paymentId,body.preparationId(),key(key),actor));
    }
    @PostMapping("/execution") public Object execute(@PathVariable String paymentId,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Execution body,Authentication auth) {
        var actor=access.require(auth,PAYMENT_OPERATOR);
        return views.exact(service.claim(paymentId,body.recommendationId(),key(key),body.expectedVersion(),body.quoteId(),body.quoteVersion(),actor));
    }
    @PostMapping("/recovery-authorization") public Object authorize(@PathVariable String paymentId,@Valid @RequestBody Release body,Authentication auth) {
        service.authorizeRelease(paymentId,body.executionId(),body.sourceHash(),access.require(auth,PAYMENT_APPROVER));
        return java.util.Map.of("status","AUTHORIZED","action","RELEASE_REVERTED_TOKEN_IN_FLIGHT");
    }
    private static UUID key(String key) {
        try {var value=UUID.fromString(key);if(!value.toString().equalsIgnoreCase(key))throw new IllegalArgumentException();return value;}
        catch(IllegalArgumentException invalid){throw new ApprovalConflictException("UUID_IDEMPOTENCY_KEY_REQUIRED");}
    }
}
