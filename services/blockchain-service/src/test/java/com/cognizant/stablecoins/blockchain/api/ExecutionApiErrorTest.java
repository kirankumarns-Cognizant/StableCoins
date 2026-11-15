package com.cognizant.stablecoins.blockchain.api;
import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import com.cognizant.stablecoins.blockchain.repository.BlockchainTransactionRepository;
import com.cognizant.stablecoins.blockchain.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ExecutionApiErrorTest {
    BlockchainTransactionService execution=mock(BlockchainTransactionService.class);
    MockMvc mvc=MockMvcBuilders.standaloneSetup(new BlockchainTransactionController(execution,mock(BlockchainTransactionRepository.class),new BlockchainProperties(),mock(com.cognizant.stablecoins.platform.security.PaymentOwnership.class)))
        .setControllerAdvice(new BlockchainApiExceptionHandler()).build();
    static final String REQUEST="{\"paymentId\":\"PAY-1\",\"correlationId\":\"CORR-1\",\"toAddress\":\"0x1111111111111111111111111111111111111111\",\"amount\":1}";
    @Test void invalidBodyHasStructuredError() throws Exception {
        mvc.perform(post("/api/v1/blockchain/transfers").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TRANSFER_REQUEST"));verifyNoInteractions(execution);
    }
    @Test void rpcFailureDoesNotExposeProviderUrlOrCredentials() throws Exception {
        when(execution.prepareTransfer(any(),any(),any(),any())).thenThrow(new BlockchainRpcException("https://rpc.invalid/secret-token"));
        mvc.perform(post("/api/v1/blockchain/transfers").contentType("application/json").content(REQUEST))
            .andExpect(status().isServiceUnavailable()).andExpect(content().json("{\"code\":\"BLOCKCHAIN_RPC_UNAVAILABLE\"}"));
    }
    @Test void reservationConflictHasStructuredError() throws Exception {
        when(execution.prepareTransfer(any(),any(),any(),any())).thenThrow(new IllegalStateException("PAYMENT_OPERATION_CONFLICT"));
        mvc.perform(post("/api/v1/blockchain/transfers").contentType("application/json").content(REQUEST))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXECUTION_STATE_CONFLICT"));
    }
}
