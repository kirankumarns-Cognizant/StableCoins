package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.cognizant.stablecoins.payment.support.IntentFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class PaymentFlowServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 final MockEnvironment env=new MockEnvironment().withProperty("app.flow.execution-enabled","true");final FlowReadModels reads=mock(FlowReadModels.class);
 final PaymentRepository payments=mock(PaymentRepository.class);final AccountRepository accounts=mock(AccountRepository.class);final BeneficiaryRepository beneficiaries=mock(BeneficiaryRepository.class);
 final RecommendationService approvals=mock(RecommendationService.class);final OutboxEventRepository outbox=mock(OutboxEventRepository.class);final PaymentStatusHistoryRepository history=mock(PaymentStatusHistoryRepository.class);
 final UUID id=UUID.randomUUID(),txId=UUID.randomUUID(),recId=UUID.randomUUID();
 final Request request=new Request(UUID.randomUUID(),"PAY-1","CORR-1",IntentFixtures.CUSTOMER,new BigDecimal("10000"),new BigDecimal("10000"),"0x"+"1".repeat(40),"USDC_WALLET","AUTO","USD-USDC-WALLET",IntentFixtures.DEADLINE,Set.of("SIMULATED-PROVIDER-A"));
 final Quote quote=new Quote(request.requestId(),UUID.randomUUID(),1,"SIMULATED-PROVIDER-A","v1",request.sourceUsd(),request.transferUsdc(),BigDecimal.ONE,990,1000,120000,BigDecimal.TEN,true,Instant.now(),Instant.now().plusSeconds(300));
 PaymentFlowService service;Payment p;Account account;Execution execution;
 @BeforeEach void setup(){
  service=new PaymentFlowService(jdbc,mapper,env,payments,accounts,beneficiaries,approvals,outbox,history,mock(OwnerEvidenceAssembler.class),reads);
  p=new Payment();p.setPaymentId(request.paymentId());p.setCorrelationId(request.correlationId());p.setStatus("COMPLIANCE_APPROVED");p.setSenderAccountId(UUID.randomUUID());p.setAmount(request.sourceUsd());
  p.setIntent(new PaymentIntentSnapshot(UUID.randomUUID(),1,request.customerId(),"creator",Instant.now(),SettlementAsset.USDC,true,true,IntentFixtures.intent(BeneficiaryOutcome.USDC_WALLET),IntentFixtures.policy("v1")));
  account=IntentFixtures.account();account.setBalance(new BigDecimal("20000"));
  state("PREPARING");when(reads.execution(id)).thenAnswer(i->execution);when(reads.validated(id)).thenReturn(true);when(reads.quote(request.requestId())).thenReturn(Optional.of(new Stored<>("READY",quote,null)));
  when(payments.lockByPaymentId(request.paymentId())).thenReturn(Optional.of(p));when(accounts.lockByAccountId(p.getSenderAccountId())).thenReturn(Optional.of(account));
  when(jdbc.queryForObject(contains("select actor_id"),eq(String.class),eq(id))).thenReturn("operator");
  when(jdbc.queryForList(contains("from blockchain_transaction"),eq(request.paymentId()))).thenReturn(List.of(Map.of("transaction_id",txId,"correlation_id",request.correlationId(),"to_address",request.recipientWallet(),"amount",request.transferUsdc(),"status","PREPARED","reservation_status","HELD")));
  when(jdbc.queryForObject(contains("select reservation_expires_at"),eq(OffsetDateTime.class),eq(txId))).thenReturn(OffsetDateTime.now().plusMinutes(5));
 }
 void state(String status){execution=new Execution(id,recId,request.requestId(),request.paymentId(),request.correlationId(),status,request,txId,Instant.now().plusSeconds(120));}
 @Test void fundsOnlyOnceAndWaitsForLedgerBeforeSubmission() throws Exception {
  service.advance(id);assertThat(account.getBalance()).isEqualByComparingTo("10000");verify(accounts).save(account);verify(approvals).markExecuted(eq(request.paymentId()),eq(recId),any());
  var events=ArgumentCaptor.forClass(OutboxEvent.class);verify(outbox).save(events.capture());var event=mapper.readTree(events.getValue().getPayload());assertThat(event.path("eventType").asText()).isEqualTo("StablecoinFundingCompleted");assertThat(event.path("eventVersion").asText()).isEqualTo("2.0");assertThat(event.path("payload").path("simulation").asBoolean()).isTrue();assertThat(event.path("payload").path("terms").path("conversionId").asText()).isEqualTo(id.toString());
  state("AWAITING_LEDGER");service.advance(id);verifyNoMoreInteractions(outbox);verify(accounts,times(1)).save(account);assertThat(account.getBalance()).isEqualByComparingTo("10000");
 }
 @Test void insufficientUsdDoesNotDebitOrPublishFunding(){account.setBalance(BigDecimal.ONE);assertThatThrownBy(()->service.advance(id)).extracting("errorCode").isEqualTo("INSUFFICIENT_SIMULATED_USD");verifyNoInteractions(outbox);verify(accounts,never()).save(any());verifyNoInteractions(approvals);}
 @Test void missingComplianceValidationCannotDebit(){when(reads.validated(id)).thenReturn(false);service.advance(id);verifyNoInteractions(accounts,outbox,approvals);}
 @Test void submissionIsOnlyRequestedAfterBoundLedgerAcknowledgement(){state("AWAITING_LEDGER");when(reads.funded(execution,quote)).thenReturn(true);service.advance(id);var events=ArgumentCaptor.forClass(OutboxEvent.class);verify(outbox).save(events.capture());assertThat(events.getValue().getEventType()).isEqualTo("BlockchainSubmissionRequested");verifyNoInteractions(accounts);}
 @Test void disabledExecutionStillPreventsNewFunding(){env.setProperty("app.flow.execution-enabled","false");service.advance(id);verifyNoInteractions(accounts,outbox,approvals);}
 @Test void unknownSignedTransactionCannotCreateAnotherLeg(){state("SUBMISSION_REQUESTED");when(jdbc.queryForList(contains("from blockchain_transaction"),eq(request.paymentId()))).thenReturn(List.of(Map.of("transaction_id",txId,"correlation_id",request.correlationId(),"to_address",request.recipientWallet(),"amount",request.transferUsdc(),"status","SUBMISSION_UNKNOWN","reservation_status","PINNED")));service.advance(id);verifyNoInteractions(accounts,outbox,approvals);assertThat(p.getStatus()).isEqualTo("COMPLIANCE_APPROVED");}
 @Test void bankCannotFundBeforeItsExactProviderFiatReservationIsPinned(){
  var bank=new Quote(quote.requestId(),quote.quoteId(),quote.version(),quote.providerId(),quote.simulationVersion(),quote.sourceUsd(),quote.transferUsdc(),BigDecimal.ONE,990,1000,120000,BigDecimal.TEN,true,quote.createdAt(),quote.expiresAt(),"INR",new BigDecimal("830000.00"),new BigDecimal("83.00"),new BigDecimal("830000.00"));
  when(reads.quote(request.requestId())).thenReturn(Optional.of(new Stored<>("READY",bank,null)));when(reads.providerReserved(execution,bank)).thenReturn(false);
  service.advance(id);verifyNoInteractions(accounts,outbox,approvals);assertThat(account.getBalance()).isEqualByComparingTo("20000");
 }


 void markExplicit(){var old=p.getIntent();var i=old.instruction();var selected=new com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest(i.requiredOutcome(),i.deliverBy(),i.maximumFee(),i.feeCurrency(),i.priority(),i.allowedAlternatives(),i.confirmed(),new com.cognizant.stablecoins.payment.settlement.SettlementSelection("base-sepolia","0x"+"a".repeat(40),p.getAmount()));p.setIntent(new PaymentIntentSnapshot(old.intentId(),old.intentVersion(),old.customerId(),old.confirmedBy(),old.confirmedAt(),old.settlementAsset(),old.fundingSimulated(),old.fiatPayoutSimulated(),selected,old.policy()));}
 @Test void explicitSelectionCannotEnterLegacyPreparationOrExecution(){markExplicit();assertThatThrownBy(()->service.prepare(p.getPaymentId(),UUID.randomUUID(),IntentFixtures.ACTOR)).extracting("errorCode").isEqualTo("SETTLEMENT_WORKFLOW_ACCEPTANCE_PENDING");assertThatThrownBy(()->service.execute(p.getPaymentId(),recId,id,1,quote.quoteId(),1,IntentFixtures.ACTOR)).extracting("errorCode").isEqualTo("SETTLEMENT_WORKFLOW_ACCEPTANCE_PENDING");verifyNoInteractions(accounts,outbox,approvals);verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @Test void explicitSelectionCannotDebitUsdThroughLegacyWorker(){markExplicit();assertThatThrownBy(()->service.advance(id)).extracting("errorCode").isEqualTo("SETTLEMENT_WORKFLOW_ACCEPTANCE_PENDING");verifyNoInteractions(accounts,outbox,approvals);assertThat(account.getBalance()).isEqualByComparingTo("20000");}
}
