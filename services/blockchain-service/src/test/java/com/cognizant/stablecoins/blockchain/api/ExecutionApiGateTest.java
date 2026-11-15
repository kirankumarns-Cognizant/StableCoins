package com.cognizant.stablecoins.blockchain.api;
import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import com.cognizant.stablecoins.blockchain.repository.BlockchainTransactionRepository;
import com.cognizant.stablecoins.blockchain.service.BlockchainTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;
/** Default configuration cannot expose the legacy execution controller independently of the authentication filter, which is tested in platform-security. */
@WebMvcTest(controllers=BlockchainTransactionController.class,properties="blockchain.execution-api-enabled=false")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters=false)
class ExecutionApiGateTest {
    @Autowired MockMvc mvc;
    @MockBean BlockchainTransactionService execution;
    @MockBean BlockchainTransactionRepository repository;
    @MockBean BlockchainProperties properties;
    @Test void transferEndpointIsClosedByDefault() throws Exception {
        mvc.perform(post("/api/v1/blockchain/transfers").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(execution);
    }
}
