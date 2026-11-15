package com.cognizant.stablecoins.ledger.accounting;
import com.cognizant.stablecoins.ledger.api.LedgerController;
import com.cognizant.stablecoins.ledger.repository.LedgerAccountRepository;
import com.cognizant.stablecoins.ledger.service.LedgerPostingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ManualLedgerWriteTest {
    @Test void manualPostingIsDisabledBeforeCallingFinancialService() throws Exception {
        var service=mock(LedgerPostingService.class);var mvc=MockMvcBuilders.standaloneSetup(new LedgerController(service,mock(LedgerAccountRepository.class),false,mock(com.cognizant.stablecoins.platform.security.PaymentOwnership.class))).build();
        mvc.perform(post("/api/v1/journals").contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void manualReversalIsDisabledBeforeCallingFinancialService() throws Exception {
        var service=mock(LedgerPostingService.class);var mvc=MockMvcBuilders.standaloneSetup(new LedgerController(service,mock(LedgerAccountRepository.class),false,mock(com.cognizant.stablecoins.platform.security.PaymentOwnership.class))).build();
        mvc.perform(post("/api/v1/journals/"+UUID.randomUUID()+"/reverse")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
}
