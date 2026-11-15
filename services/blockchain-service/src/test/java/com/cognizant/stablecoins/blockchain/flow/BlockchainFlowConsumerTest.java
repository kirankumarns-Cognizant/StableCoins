package com.cognizant.stablecoins.blockchain.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.blockchain.service.BlockchainTransactionService;
import com.cognizant.stablecoins.blockchain.repository.BlockchainTransactionRepository;
import com.cognizant.stablecoins.blockchain.model.BlockchainTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class BlockchainFlowConsumerTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();final FlowReadModels reads=mock(FlowReadModels.class);
 final MockEnvironment env=new MockEnvironment().withProperty("app.flow.execution-enabled","true");final BlockchainTransactionService chain=mock(BlockchainTransactionService.class);final BlockchainTransactionRepository transactions=mock(BlockchainTransactionRepository.class);
 final UUID id=UUID.randomUUID(),txId=UUID.randomUUID();final Request request=new Request(UUID.randomUUID(),"PAY-1","CORR-1",UUID.randomUUID(),new BigDecimal("10000"),new BigDecimal("10000"),"0x"+"1".repeat(40),"USDC_WALLET","AUTO","USD-USDC-WALLET",Instant.now().plusSeconds(300),Set.of("SIMULATED-PROVIDER-A"));
 final Execution execution=new Execution(id,UUID.randomUUID(),request.requestId(),request.paymentId(),request.correlationId(),"SUBMISSION_REQUESTED",request,txId,Instant.now().plusSeconds(120));
 final Quote quote=new Quote(request.requestId(),UUID.randomUUID(),1,"SIMULATED-PROVIDER-A","v1",request.sourceUsd(),request.transferUsdc(),BigDecimal.ONE,990,1000,120000,BigDecimal.TEN,true,Instant.now(),Instant.now().plusSeconds(300));
 BlockchainFlowConsumer consumer;BlockchainTransaction tx;
 String body(String type){return FlowJson.json(mapper,new FlowJson.Envelope(UUID.randomUUID().toString(),type,"1.0",request.paymentId(),request.correlationId(),Instant.now(),mapper.valueToTree(new Command(id))));}
 @BeforeEach void setup(){consumer=new BlockchainFlowConsumer(jdbc,mapper,env,chain,transactions,reads);when(reads.execution(id)).thenReturn(execution);when(reads.validated(id)).thenReturn(true);when(reads.quote(request.requestId())).thenReturn(Optional.of(new Stored<>("READY",quote,null)));tx=new BlockchainTransaction();tx.setTransactionId(txId);tx.setPaymentId(request.paymentId());tx.setCorrelationId(request.correlationId());tx.setToAddress(request.recipientWallet());tx.setAmount(request.transferUsdc());tx.setStatus("PREPARED");when(transactions.findById(txId)).thenReturn(Optional.of(tx));}
 @Test void doesNotSubmitWithoutPostedAllocation(){assertThatThrownBy(()->consumer.submit(body("BlockchainSubmissionRequested"))).hasMessage("LEDGER_ALLOCATION_PENDING");verifyNoInteractions(chain);}
 @Test void approvedFundedClaimUsesExistingSubmissionService(){when(reads.funded(execution,quote)).thenReturn(true);consumer.submit(body("BlockchainSubmissionRequested"));verify(chain).submitTransaction(txId);}
 @Test void unknownSubmissionRedeliveryNeverBroadcasts(){tx.setStatus("SUBMISSION_UNKNOWN");consumer.submit(body("BlockchainSubmissionRequested"));verifyNoInteractions(chain);verify(reads,never()).funded(any(),any());}
 @Test void changedRecipientCannotSubmit(){tx.setToAddress("0x"+"9".repeat(40));assertThatThrownBy(()->consumer.submit(body("BlockchainSubmissionRequested"))).hasMessage("TRANSACTION_BINDING_MISMATCH");verifyNoInteractions(chain);}
 @Test void disabledExecutionCannotSubmit(){env.setProperty("app.flow.execution-enabled","false");assertThatThrownBy(()->consumer.submit(body("BlockchainSubmissionRequested"))).hasMessage("EXECUTION_DISABLED");verifyNoInteractions(chain);}
 @Test void reservationWaitsForCompliance(){when(reads.validated(id)).thenReturn(false);assertThatThrownBy(()->consumer.reserve(body("StablecoinExecutionRequested"))).hasMessage("COMPLIANCE_VALIDATION_PENDING");verifyNoInteractions(chain);}
 @Test void receiptMonitoringContinuesWhenNewExecutionDisabled(){env.setProperty("app.flow.execution-enabled","false");tx.setStatus("SUBMISSION_UNKNOWN");when(transactions.findByStatus("SUBMISSION_UNKNOWN")).thenReturn(List.of(tx));consumer.receipts();verify(chain).confirmTransaction(txId);verify(chain,never()).submitTransaction(any());}
}
