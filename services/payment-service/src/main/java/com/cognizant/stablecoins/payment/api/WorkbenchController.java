package com.cognizant.stablecoins.payment.api;

import com.cognizant.stablecoins.payment.security.CustomerAccess;
import com.cognizant.stablecoins.payment.workbench.WorkbenchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR;

/** Customer-authorized read projection. Commands remain on their existing owner APIs. */
@RestController
@RequestMapping("/api/v1/payments")
@ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class WorkbenchController {
 private final WorkbenchService views; private final CustomerAccess access;
 public WorkbenchController(WorkbenchService views,CustomerAccess access){this.views=views;this.access=access;}
 @GetMapping("/workbench") public Object catalog(Authentication auth){return views.catalog(access.require(auth,PAYMENT_OPERATOR));}
 @GetMapping("/{paymentId}/evidence") public Object evidence(@PathVariable String paymentId,Authentication auth){return views.evidence(paymentId,access.require(auth,PAYMENT_OPERATOR));}
}
