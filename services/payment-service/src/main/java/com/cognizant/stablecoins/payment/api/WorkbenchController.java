package com.cognizant.stablecoins.payment.api;

import com.cognizant.stablecoins.payment.security.CustomerAccess;
import com.cognizant.stablecoins.payment.workbench.WorkbenchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.LinkedHashMap;
import org.springframework.web.bind.annotation.*;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR;

/** Customer-authorized read projection. Commands remain on their existing owner APIs. */
@RestController
@RequestMapping("/api/v1/payments")
@ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class WorkbenchController {
 private final WorkbenchService views; private final CustomerAccess access;
 public WorkbenchController(WorkbenchService views,CustomerAccess access){this.views=views;this.access=access;}
 @GetMapping("/workbench") public Object catalog(Authentication auth){
  var actor=access.require(auth,PAYMENT_OPERATOR);
  var result=new LinkedHashMap<>(views.catalog(actor));
  // Display only: the JWT is validated by resource-server security before membership resolution.
  Object username=auth instanceof JwtAuthenticationToken token?token.getToken().getClaims().get("username"):null;
  result.put("actorUsername",username instanceof String name&&!name.isBlank()&&name.length()<=128&&name.codePoints().noneMatch(Character::isISOControl)?name:actor.actorId());
  return result;
 }
 @GetMapping("/{paymentId}/evidence") public Object evidence(@PathVariable String paymentId,Authentication auth){return views.evidence(paymentId,access.require(auth,PAYMENT_OPERATOR));}
}
