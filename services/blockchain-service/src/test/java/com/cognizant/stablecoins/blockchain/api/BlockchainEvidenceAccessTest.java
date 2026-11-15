package com.cognizant.stablecoins.blockchain.api;
import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import com.cognizant.stablecoins.blockchain.model.BlockchainTransaction;
import com.cognizant.stablecoins.blockchain.repository.BlockchainTransactionRepository;
import com.cognizant.stablecoins.blockchain.service.BlockchainTransactionService;
import com.cognizant.stablecoins.platform.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class BlockchainEvidenceAccessTest {
 final BlockchainTransactionRepository repository=mock(BlockchainTransactionRepository.class);
 final PaymentOwnership ownership=mock(PaymentOwnership.class);
 final org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new BlockchainTransactionController(mock(BlockchainTransactionService.class),repository,new BlockchainProperties(),ownership)).setControllerAdvice(new PlatformAccessErrors()).build();
 @Test void foreignPaymentIsDeniedBeforeReadingChainRecord()throws Exception{
  doThrow(new AccessFailure("PAYMENT_NOT_FOUND",404)).when(ownership).require(eq("PAY-1"),isNull());
  mvc.perform(get("/api/v1/blockchain/payments/PAY-1/transaction")).andExpect(status().isNotFound());verifyNoInteractions(repository);
 }
 @Test void transactionIdDoesNotBypassPaymentOwnership()throws Exception{
  var id=UUID.randomUUID();var tx=mock(BlockchainTransaction.class);when(tx.getPaymentId()).thenReturn("PAY-1");when(repository.findById(id)).thenReturn(Optional.of(tx));
  doThrow(new AccessFailure("PAYMENT_NOT_FOUND",404)).when(ownership).require(eq("PAY-1"),isNull());
  mvc.perform(get("/api/v1/blockchain/transactions/"+id)).andExpect(status().isNotFound());verify(tx,never()).getTxHash();
 }
 @Test void ownershipStoreFailureIsSanitized()throws Exception{
  doThrow(new AccessFailure("EVIDENCE_STORE_UNAVAILABLE",503)).when(ownership).require(eq("PAY-1"),isNull());
  mvc.perform(get("/api/v1/blockchain/payments/PAY-1/transaction")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EVIDENCE_STORE_UNAVAILABLE"));verifyNoInteractions(repository);
 }
}
