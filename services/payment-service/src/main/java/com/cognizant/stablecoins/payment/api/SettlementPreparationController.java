package com.cognizant.stablecoins.payment.api;

import com.cognizant.stablecoins.flow.SettlementPreparation;
import com.cognizant.stablecoins.payment.settlement.SettlementPreparationService;
import com.cognizant.stablecoins.payment.security.CustomerAccess;
import com.cognizant.stablecoins.payment.approval.ApprovalConflictException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR;

/** Disabled by default; observation-only engineering API, not a Besu payment selection switch. */
@RestController
@RequestMapping("/api/v1/payments/{paymentId}/settlement-observation-preparations")
@ConditionalOnProperty(name={"app.flow.enabled","app.besu.observation-api-enabled"},havingValue="true")
public class SettlementPreparationController {
    private final SettlementPreparationService service;
    private final CustomerAccess access;
    public SettlementPreparationController(SettlementPreparationService service,CustomerAccess access){
        this.service=service;this.access=access;
    }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public SettlementPreparation.Request prepare(@PathVariable String paymentId,
            @RequestHeader("Idempotency-Key") String key,Authentication authentication){
        UUID id;
        try{id=UUID.fromString(key);if(!id.toString().equalsIgnoreCase(key))throw new IllegalArgumentException();}
        catch(IllegalArgumentException error){throw new ApprovalConflictException("UUID_IDEMPOTENCY_KEY_REQUIRED");}
        return service.prepare(paymentId,id,access.require(authentication,PAYMENT_OPERATOR));
    }
    @GetMapping("/{id}")
    public SettlementPreparationService.View get(@PathVariable String paymentId,@PathVariable UUID id,Authentication authentication){
        return service.get(paymentId,id,access.require(authentication,PAYMENT_OPERATOR));
    }
}
