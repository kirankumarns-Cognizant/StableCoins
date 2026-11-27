package com.cognizant.stablecoins.ledger.besu;
import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class BesuFundingHandlerTest {
    @Test void untrustedCommandCannotInvokeAccounting(){
        var reads=mock(SettlementFundingReads.class);var accounting=mock(BesuAccountingService.class);
        doThrow(new IllegalStateException("COMMITTED_BESU_FUNDING_REQUIRED")).when(reads).requireCommand("invalid");
        assertThatThrownBy(()->new BesuFundingHandler(reads,accounting).handle("invalid")).hasMessage("COMMITTED_BESU_FUNDING_REQUIRED");verifyNoInteractions(accounting);
    }
    @Test void redeliveryUsesSameExecutionAndReference(){
        var reads=mock(SettlementFundingReads.class);var accounting=mock(BesuAccountingService.class);var e=BesuAccountingTestData.execution("PAY-HANDOFF");
        when(reads.requireCommand("committed")).thenReturn(e);var handler=new BesuFundingHandler(reads,accounting);
        handler.handle("committed");handler.handle("committed");verify(accounting,times(2)).fundAndAllocate(e,SettlementFundingReads.reference(e));
    }
}
