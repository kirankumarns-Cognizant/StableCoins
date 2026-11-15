package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.security.CustomerAccess;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR;
@RestController @RequestMapping("/api/v1/payments/{paymentId}/recommendations/{id}/agent-runs")
public class AgentController {
    private final AgentRunService service;private final CustomerAccess access;
    public AgentController(AgentRunService service,CustomerAccess access){this.service=service;this.access=access;}
    public record Request(@NotBlank @Size(max=2000)String instruction){@Override public String toString(){return "AgentRequest[redacted]";} }
    @PostMapping public ResponseEntity<AgentRunView> start(@PathVariable String paymentId,@PathVariable UUID id,
            @RequestHeader("Idempotency-Key")String key,@Valid @RequestBody Request body,Authentication auth){
        var view=service.start(paymentId,id,access.require(auth,PAYMENT_OPERATOR),key,body.instruction());
        return ResponseEntity.status(view.status()==RoutingAgentRun.Status.RUNNING?HttpStatus.ACCEPTED:HttpStatus.OK).body(view);
    }
    @GetMapping("/{runId}") public AgentRunView get(@PathVariable String paymentId,@PathVariable UUID id,@PathVariable UUID runId,Authentication auth){
        return service.get(paymentId,id,runId,access.require(auth,PAYMENT_OPERATOR));
    }
}
