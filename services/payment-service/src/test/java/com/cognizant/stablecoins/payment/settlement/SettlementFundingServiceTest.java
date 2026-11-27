package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import com.cognizant.stablecoins.payment.domain.SupportedCurrency;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.payment.settlement.BindingFixtures.*;

class SettlementFundingServiceTest {
    final PaymentRepository payments=mock(PaymentRepository.class);
    final AccountRepository accounts=mock(AccountRepository.class);
    final SettlementExecutionClaimService authority=mock(SettlementExecutionClaimService.class);
    final RecommendationService recommendations=mock(RecommendationService.class);
    final SettlementExecutionReads claims=mock(SettlementExecutionReads.class);
    final SettlementReservationReads reservations=mock(SettlementReservationReads.class);
    final SettlementFundingStore store=mock(SettlementFundingStore.class);
    final OutboxEventRepository outbox=mock(OutboxEventRepository.class);
    final PaymentStatusHistoryRepository history=mock(PaymentStatusHistoryRepository.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final SettlementBinding.Execution e=new SettlementBinding.Execution(UUID.randomUUID(),review(instruction()));
    final AuthorizedCustomer actor=new AuthorizedCustomer(e.instruction().customerId(),"operator");
    final Instant now=e.review().assessedAt().plusSeconds(5);
    final Payment payment=new Payment();final Account account=new Account();
    SettlementFundingService service;
    @BeforeEach void setup() {
        var i=e.instruction();
        var request=new PaymentIntentRequest(BeneficiaryOutcome.TOKEN_WALLET,i.deliverBy(),i.maximumFeeUsd(),SupportedCurrency.USD,
                RoutingPriority.LOWEST_COST,Set.of(),true,new SettlementSelection("besu-local",i.context().token().contract(),i.tokenAmount()));
        var policy=new CustomerRoutingPolicy("private-v1",Set.of(BeneficiaryOutcome.TOKEN_WALLET),Set.of(e.review().providerId()),new BigDecimal("3"),2);
        payment.setIntent(new PaymentIntentSnapshot(i.intentId(),i.intentVersion(),i.customerId(),actor.actorId(),e.review().assessedAt(),
                SettlementAsset.DEMOUSD,true,false,request,policy,new ConfirmedSettlement(i.context(),i.tokenAmount(),i.recipient())));
        payment.setPaymentId(i.paymentId());payment.setCorrelationId(i.correlationId());payment.setAmount(i.sourceUsd());
        payment.setSourceCurrency("USD");payment.setPaymentRail("STABLECOIN");payment.setStatus("COMPLIANCE_APPROVED");
        account.setAccountId(UUID.randomUUID());payment.setSenderAccountId(account.getAccountId());account.setCustomerId(i.customerId());
        account.setCurrency("USD");account.setStatus("ACTIVE");account.setAccountType("DEMO_SIMULATED_USD");account.setBalance(new BigDecimal("20"));
        when(payments.lockByPaymentId(i.paymentId())).thenReturn(Optional.of(payment));
        when(accounts.lockByAccountId(account.getAccountId())).thenReturn(Optional.of(account));
        service=new SettlementFundingService(payments,accounts,authority,recommendations,claims,reservations,store,outbox,history,mapper,Clock.fixed(now,ZoneOffset.UTC));
    }
    SettlementFundingService.Receipt fund(){return service.fund(e,actor);}
    void noDebit(){assertThat(account.getBalance()).isEqualByComparingTo("20");verify(accounts,never()).save(any());verifyNoInteractions(outbox,history);verify(store,never()).capture(any());}
    @Test void debitsExactlyPrincipalAndCapturesQualifiedSimulatedEventAndHistory() throws Exception {
        var receipt=fund();assertThat(account.getBalance()).isEqualByComparingTo("15");
        assertThat(receipt.fundingReference()).isEqualTo("SIMULATED-BESU-"+e.executionId());
        var event=ArgumentCaptor.forClass(OutboxEvent.class);verify(outbox).saveAndFlush(event.capture());
        var json=mapper.readTree(event.getValue().getPayload());
        assertThat(json.path("eventType").asText()).isEqualTo("BesuFundingAuthorized");
        assertThat(json.path("eventVersion").asText()).isEqualTo("1.0");
        assertThat(json.path("paymentId").asText()).isEqualTo(e.instruction().paymentId());
        assertThat(json.path("correlationId").asText()).isEqualTo(e.instruction().correlationId());
        assertThat(json.path("timestamp").isMissingNode()).isFalse();
        var body=json.path("payload");assertThat(body.path("simulated").asBoolean()).isTrue();
        assertThat(body.path("executionFingerprint").asText()).isEqualTo(SettlementBindingJson.fingerprint(e));
        assertThat(SettlementBindingJson.read(body.path("execution").toString(),SettlementBinding.Execution.class)).isEqualTo(e);
        var states=ArgumentCaptor.forClass(PaymentStatusHistory.class);verify(history,times(4)).save(states.capture());
        assertThat(states.getAllValues()).extracting(PaymentStatusHistory::getNewStatus).containsExactly("QUOTE_ACCEPTED","RAIL_SELECTED","FUNDING_PENDING","FUNDED");
        assertThat(payment.getStatus()).isEqualTo("FUNDED");verify(store).capture(receipt);
        var order=inOrder(accounts,authority,recommendations,reservations,outbox,store);
        order.verify(accounts).lockByAccountId(account.getAccountId());order.verify(authority).requireFundingAuthority(e,actor);
        order.verify(recommendations).markSettlementExecuted(e,actor);order.verify(reservations).requireFreshPinned(e,now);
        order.verify(accounts).save(account);order.verify(outbox).saveAndFlush(any());order.verify(store).capture(receipt);
    }
    @Test @SuppressWarnings({"unchecked","rawtypes"}) void producedCommandMatchesSharedCommittedFundingReader() throws Exception {
        var receipt=fund();var captured=ArgumentCaptor.forClass(OutboxEvent.class);verify(outbox).saveAndFlush(captured.capture());
        var event=captured.getValue();var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var row=new HashMap<String,Object>();var i=e.instruction();
        row.put("execution_document",SettlementBindingJson.write(e));row.put("execution_fingerprint",SettlementBindingJson.fingerprint(e));
        row.put("execution_id",e.executionId());row.put("payment_id",i.paymentId());row.put("correlation_id",i.correlationId());
        row.put("customer_id",i.customerId());row.put("account_customer_id",i.customerId());
        row.put("account_id",receipt.accountId());row.put("sender_account_id",receipt.accountId());row.put("source_usd",i.sourceUsd());
        row.put("balance_before",receipt.balanceBefore());row.put("balance_after",receipt.balanceAfter());row.put("actor_id",receipt.actorId());
        row.put("funded_at",java.sql.Timestamp.from(receipt.fundedAt()));row.put("funding_reference",receipt.fundingReference());
        row.put("event_type",event.getEventType());row.put("event_version",event.getEventVersion());row.put("aggregate_id",event.getAggregateId());
        row.put("event_correlation_id",event.getCorrelationId());row.put("event_document",event.getPayload());row.put("event_id",event.getEventId());
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class))).thenAnswer(c->{
            var rs=mock(java.sql.ResultSet.class);when(rs.getString(anyString())).thenAnswer(a->row.get(a.getArgument(0)));
            when(rs.getString(1)).thenReturn(event.getPayload());when(rs.getObject(anyString(),eq(UUID.class))).thenAnswer(a->row.get(a.getArgument(0)));
            when(rs.getBigDecimal(anyString())).thenAnswer(a->row.get(a.getArgument(0)));when(rs.getTimestamp(anyString())).thenAnswer(a->row.get(a.getArgument(0)));
            return List.of(((org.springframework.jdbc.core.RowMapper)c.getArgument(1)).mapRow(rs,0));
        });
        var reads=new SettlementFundingReads(jdbc,mapper,new BesuClosureEvidence.Policy("adr015-v1","110002","110001","110003"));
        assertThat(reads.requireCommand(event.getPayload())).isEqualTo(e);
    }
    @Test void exactReplayReturnsOriginalEvenAfterStateAdvancesWithoutAnotherDebitOrEvent() {
        var original=fund();clearInvocations(accounts,authority,recommendations,reservations,outbox,history,store);
        when(store.find(e)).thenReturn(Optional.of(original));payment.setStatus("RECONCILED");
        assertThat(fund()).isSameAs(original);verifyNoInteractions(accounts,authority,recommendations,reservations,outbox,history);verify(store,never()).capture(any());
    }
    @Test void noIdentityCannotReadOwners(){assertThatThrownBy(()->service.fund(e,null)).hasMessage("AUTHENTICATION_REQUIRED");verifyNoInteractions(payments,accounts,claims);}
    @Test void otherCustomerCannotReadOwners(){assertThatThrownBy(()->service.fund(e,new AuthorizedCustomer(UUID.randomUUID(),"other"))).hasMessage("CUSTOMER_SCOPE_MISMATCH");verifyNoInteractions(payments,accounts,claims);}
    @Test void unclaimedTermsCannotDebit(){doThrow(new IllegalStateException("CLAIM_REQUIRED")).when(claims).requireClaimed(e);assertThatThrownBy(this::fund).hasMessage("CLAIM_REQUIRED");noDebit();}
    @Test void revokedApproverCannotDebit(){doThrow(new IllegalStateException("ROLE_REVOKED")).when(authority).requireFundingAuthority(e,actor);assertThatThrownBy(this::fund).hasMessage("ROLE_REVOKED");noDebit();}
    @Test void changedApprovalCannotDebit(){doThrow(new ApprovalConflictException("APPROVAL_REQUIRED")).when(recommendations).markSettlementExecuted(e,actor);assertThatThrownBy(this::fund).hasMessageContaining("APPROVAL_REQUIRED");noDebit();}
    @Test void expiredOrResolvedReservationCannotDebit(){doThrow(new IllegalStateException("RESERVATION_STALE")).when(reservations).requireFreshPinned(e,now);assertThatThrownBy(this::fund).hasMessage("RESERVATION_STALE");noDebit();}
    @Test void insufficientFundsCannotExecuteRecommendation(){account.setBalance(BigDecimal.ONE);assertThatThrownBy(this::fund).extracting("errorCode").isEqualTo("INSUFFICIENT_SIMULATED_USD");verifyNoInteractions(recommendations,outbox,history);verify(accounts,never()).save(any());}
    @ParameterizedTest @ValueSource(strings={"INACTIVE","FROZEN"}) void inactiveAccountCannotDebit(String status){account.setStatus(status);assertThatThrownBy(this::fund).extracting("errorCode").isEqualTo("SIMULATED_FUNDING_BINDING_CHANGED");noDebit();}
    @Test void realAccountCannotBeDebitedAsSimulation(){account.setAccountType("REAL_USD");assertThatThrownBy(this::fund).extracting("errorCode").isEqualTo("SIMULATED_FUNDING_BINDING_CHANGED");noDebit();}
    @Test void accountOfOtherCustomerCannotDebit(){account.setCustomerId(UUID.randomUUID());assertThatThrownBy(this::fund).extracting("errorCode").isEqualTo("SIMULATED_FUNDING_BINDING_CHANGED");noDebit();}
    @Test void changedComplianceCannotDebit(){payment.setStatus("FAILED");assertThatThrownBy(this::fund).extracting("errorCode").isEqualTo("SIMULATED_FUNDING_BINDING_CHANGED");noDebit();}
    @Test void storeCannotWriteWithoutTransaction(){var r=new SettlementFundingService.Receipt(e,account.getAccountId(),new BigDecimal("20"),new BigDecimal("15"),actor.actorId(),UUID.randomUUID(),now);var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);assertThatThrownBy(()->new SettlementFundingStore(jdbc).capture(r)).hasMessage("PAYMENT_OWNER_TRANSACTION_REQUIRED");verifyNoInteractions(jdbc);}
    @Test void fundingRemainsDisabledWithoutExplicitProperty(){var a=SettlementFundingService.class.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);assertThat(a.name()).containsExactly("app.besu.funding.enabled");assertThat(a.matchIfMissing()).isFalse();}
}
