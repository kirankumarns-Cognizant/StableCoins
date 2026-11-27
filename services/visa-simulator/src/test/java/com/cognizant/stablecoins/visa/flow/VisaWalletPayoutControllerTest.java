package com.cognizant.stablecoins.visa.flow;

import com.cognizant.stablecoins.platform.security.AccessFailure;
import com.cognizant.stablecoins.platform.security.InternalServiceAccess;
import com.cognizant.stablecoins.visa.liquidity.VisaPaymentAdapter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisaWalletPayoutControllerTest {
    final VisaPaymentAdapter adapter=mock(VisaPaymentAdapter.class);
    final InternalServiceAccess internal=mock(InternalServiceAccess.class);
    final Authentication auth=mock(Authentication.class);
    final MockEnvironment env=new MockEnvironment();
    final VisaWalletPayoutController controller=new VisaWalletPayoutController(adapter,internal,env);
    @Test void operationsAreDisabledByDefaultEvenForInternalCaller(){
        assertThatThrownBy(()->controller.get(UUID.randomUUID(),auth)).isInstanceOf(AccessFailure.class);
        verify(internal).require(auth);verifyNoInteractions(adapter);
    }
    @Test void userCannotReachSimulatorByEnablingOperations(){
        env.setProperty("visa.simulator.operations-enabled","true");
        doThrow(new AccessFailure("INTERNAL_SERVICE_ACCESS_REQUIRED",403)).when(internal).require(auth);
        assertThatThrownBy(()->controller.complete(UUID.randomUUID(),auth)).isInstanceOf(AccessFailure.class);
        verifyNoInteractions(adapter);
    }
    @Test void enabledInternalReadDelegatesOnlyToProviderAdapter(){
        env.setProperty("visa.simulator.operations-enabled","true");UUID id=UUID.randomUUID();
        controller.get(id,auth);verify(adapter).walletPayout(id);
    }
    @Test void balanceEvidenceRequiresInternalAccessAndOperationsEnabled(){
        UUID id=UUID.randomUUID();
        assertThatThrownBy(()->controller.balances(id,auth)).isInstanceOf(AccessFailure.class);
        verifyNoInteractions(adapter);
        env.setProperty("visa.simulator.operations-enabled","true");
        controller.balances(id,auth);
        verify(adapter).walletPayoutStatement(id);
    }
    @Test void resolutionRequiresAnExplicitOutcome(){
        env.setProperty("visa.simulator.operations-enabled","true");
        assertThatThrownBy(()->controller.resolve(UUID.randomUUID(),UUID.randomUUID(),new VisaWalletPayoutController.Resolution(null),auth))
                .hasMessage("WALLET_PAYOUT_OUTCOME_REQUIRED");
        verifyNoInteractions(adapter);
    }
}