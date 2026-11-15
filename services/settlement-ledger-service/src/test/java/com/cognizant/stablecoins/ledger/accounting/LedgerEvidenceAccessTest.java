package com.cognizant.stablecoins.ledger.accounting;
import com.cognizant.stablecoins.ledger.api.LedgerController;
import com.cognizant.stablecoins.ledger.model.Journal;
import com.cognizant.stablecoins.ledger.repository.LedgerAccountRepository;
import com.cognizant.stablecoins.ledger.service.LedgerPostingService;
import com.cognizant.stablecoins.platform.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class LedgerEvidenceAccessTest {
 final LedgerPostingService ledger=mock(LedgerPostingService.class);
 final PaymentOwnership ownership=mock(PaymentOwnership.class);
 final org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new LedgerController(ledger,mock(LedgerAccountRepository.class),false,ownership)).setControllerAdvice(new PlatformAccessErrors()).build();
 @Test void foreignPaymentIsDeniedBeforeReadingJournals()throws Exception{
  doThrow(new AccessFailure("PAYMENT_NOT_FOUND",404)).when(ownership).require(eq("PAY-1"),isNull());
  mvc.perform(get("/api/v1/payments/PAY-1/ledger")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));verifyNoInteractions(ledger);
 }
 @Test void journalIdDoesNotBypassPaymentOwnership()throws Exception{
  var id=UUID.randomUUID();var journal=mock(Journal.class);when(journal.getPaymentId()).thenReturn("PAY-1");when(ledger.getJournal(id)).thenReturn(journal);
  doThrow(new AccessFailure("PAYMENT_NOT_FOUND",404)).when(ownership).require(eq("PAY-1"),isNull());
  mvc.perform(get("/api/v1/journals/"+id)).andExpect(status().isNotFound());verify(ledger,never()).getEntriesForJournal(any());
 }
 @Test void ownershipStoreFailureIsSanitized()throws Exception{
  doThrow(new AccessFailure("EVIDENCE_STORE_UNAVAILABLE",503)).when(ownership).require(eq("PAY-1"),isNull());
  mvc.perform(get("/api/v1/payments/PAY-1/ledger")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EVIDENCE_STORE_UNAVAILABLE"));verifyNoInteractions(ledger);
 }
}
