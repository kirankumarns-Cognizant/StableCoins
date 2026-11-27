package com.cognizant.stablecoins.payment.api;
import com.cognizant.stablecoins.payment.settlement.PrivateSettlementApplication;
import com.cognizant.stablecoins.payment.security.CustomerAccess;
import com.cognizant.stablecoins.payment.workbench.WorkbenchService;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.approval.ApprovalConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import java.util.UUID;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PrivateSettlementControllerTest {
 final PrivateSettlementApplication service=mock(PrivateSettlementApplication.class);
 final CustomerAccess access=mock(CustomerAccess.class);final WorkbenchService views=mock(WorkbenchService.class);
 final Authentication auth=mock(Authentication.class);final AuthorizedCustomer actor=new AuthorizedCustomer(UUID.randomUUID(),"verified-subject");
 final PrivateSettlementController controller=new PrivateSettlementController(service,access,views);
 @Test void readIsBoundToVerifiedOperator(){when(access.require(auth,PAYMENT_OPERATOR)).thenReturn(actor);controller.evidence("PAY-X",auth);verify(service).evidence("PAY-X",actor);}
 @Test void invalidExecutionKeyCannotClaimOrFund(){when(access.require(auth,PAYMENT_OPERATOR)).thenReturn(actor);
  var body=new PrivateSettlementController.Execution(UUID.randomUUID(),1,UUID.randomUUID(),1);
  assertThrows(ApprovalConflictException.class,()->controller.execute("PAY-X","1-1-1-1-1",body,auth));verifyNoInteractions(service);
 }
 @Test void replayUsesTheSameExecutionKeyAndApprovedVersion(){when(access.require(auth,PAYMENT_OPERATOR)).thenReturn(actor);
  var key=UUID.randomUUID();var body=new PrivateSettlementController.Execution(UUID.randomUUID(),2,UUID.randomUUID(),1);
  controller.execute("PAY-X",key.toString(),body,auth);controller.execute("PAY-X",key.toString(),body,auth);
  verify(service,times(2)).claim("PAY-X",body.recommendationId(),key,2,body.quoteId(),1,actor);
 }
 @Test void recoveryRequiresApproverAndPassesOnlySourceBoundIdentifiers(){when(access.require(auth,PAYMENT_APPROVER)).thenReturn(actor);
  var body=new PrivateSettlementController.Release(UUID.randomUUID(),"a".repeat(64));controller.authorize("PAY-X",body,auth);
  verify(service).authorizeRelease("PAY-X",body.executionId(),body.sourceHash(),actor);
 }
 @Test void deniedIdentityCannotReachRecovery(){when(access.require(auth,PAYMENT_APPROVER)).thenThrow(new IllegalArgumentException("DENIED"));
  assertThrows(IllegalArgumentException.class,()->controller.authorize("PAY-X",new PrivateSettlementController.Release(UUID.randomUUID(),"a".repeat(64)),auth));verifyNoInteractions(service);
 }
 @Test void payloadValidationRejectsMissingIdsVersionsAndMalformedSourceHashes(){
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {var validator=factory.getValidator();
   assertFalse(validator.validate(new PrivateSettlementController.Execution(null,0,null,0)).isEmpty());
   assertFalse(validator.validate(new PrivateSettlementController.Release(UUID.randomUUID(),"invalid")).isEmpty());
   assertFalse(validator.validate(new PrivateSettlementController.Proposal(null)).isEmpty());
  }
 }
}
