package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.payment.settlement.BindingFixtures.*;

/** Requires Docker. Minimal owner tables, V13/V14 and real JDBC rollback; not full Flyway/application acceptance. */
@Testcontainers
class SettlementPreparationPersistenceIT {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules();
    @BeforeAll static void schema() throws Exception {
        var ds=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("create table account(account_id uuid primary key,customer_id uuid,status text)");
        jdbc.execute("create table beneficiary(beneficiary_id uuid primary key,customer_id uuid,status text,wallet_address text)");
        jdbc.execute("create table payment(payment_id varchar(50) primary key,correlation_id varchar(60),sender_account_id uuid references account(account_id),beneficiary_id uuid references beneficiary(beneficiary_id),amount numeric,source_currency text,payment_rail text,status text,intent_snapshot jsonb)");
        jdbc.execute("create table routing_recommendation(recommendation_id uuid primary key)");
        jdbc.execute("create table payment_flow_preparation(request_id uuid primary key)");
        jdbc.execute("create table payment_execution(execution_id uuid primary key)");
        for(var name:List.of("V13__immutable_settlement_bindings.sql","V14__settlement_preparation_requests.sql","V15__settlement_quote_drafts.sql"))
            try(var input=new ClassPathResource("db/migration/"+name).getInputStream()){
                jdbc.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            }
    }
    static SettlementBinding.Instruction seed() throws Exception {
        var i=instruction();var account=UUID.randomUUID();var beneficiary=UUID.randomUUID();
        ObjectNode intent=MAPPER.createObjectNode();intent.put("intentId",i.intentId().toString());
        intent.put("intentVersion",i.intentVersion());intent.put("customerId",i.customerId().toString());
        var resolved=intent.putObject("settlement");resolved.set("context",MAPPER.valueToTree(i.context()));
        resolved.put("recipient",i.recipient());resolved.put("tokenAmount",i.tokenAmount());
        var body=intent.putObject("instruction");body.put("requiredOutcome",i.outcome().name());
        body.putArray("allowedAlternatives");body.put("deliverBy",i.deliverBy().toString());body.put("maximumFee",i.maximumFeeUsd());
        var selected=body.putObject("settlement");selected.put("networkId",i.context().token().chain().networkId());
        selected.put("tokenContract",i.context().token().contract());selected.put("tokenAmount",i.tokenAmount());
        jdbc.update("insert into account values (?,?,'ACTIVE')",account,i.customerId());
        jdbc.update("insert into beneficiary values (?,?,'ACTIVE',?)",beneficiary,i.customerId(),i.recipient());
        jdbc.update("insert into payment values (?,?,?,?,?,'USD','STABLECOIN','COMPLIANCE_APPROVED',cast(? as jsonb))",
                i.paymentId(),i.correlationId(),account,beneficiary,i.sourceUsd(),intent.toString());
        tx.executeWithoutResult(s->new SettlementBindingStore(jdbc).captureInstruction(i));
        return i;
    }
    static SettlementPreparationService service(OutboxEventRepository outbox){
        return new SettlementPreparationService(jdbc,MAPPER,outbox,new SettlementPreparationReads(jdbc,MAPPER),
                Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"),ZoneOffset.UTC));
    }
    @Test void requestSurvivesJsonbAndCannotBeMutated() throws Exception {
        var i=seed();var key=UUID.randomUUID();var actor=new AuthorizedCustomer(i.customerId(),"operator");
        var service=service(mock(OutboxEventRepository.class));
        var captured=tx.execute(s->service.prepare(i.paymentId(),key,actor));
        assertThat(new SettlementPreparationReads(jdbc,MAPPER).request(key)).contains(captured);
        assertThatThrownBy(()->jdbc.update("update payment_settlement_preparation set actor_id='other' where request_id=?",key))
                .hasMessageContaining("SETTLEMENT_BINDING_IMMUTABLE");
        assertThatThrownBy(()->jdbc.update("delete from payment_settlement_preparation where request_id=?",key))
                .hasMessageContaining("SETTLEMENT_BINDING_IMMUTABLE");
    }
    @Test void requestRollsBackWhenOutboxSaveFails() throws Exception {
        var i=seed();var key=UUID.randomUUID();var outbox=mock(OutboxEventRepository.class);
        when(outbox.save(any())).thenThrow(new IllegalStateException("outbox-unavailable"));
        var service=service(outbox);
        assertThatThrownBy(()->tx.execute(s->service.prepare(i.paymentId(),key,new AuthorizedCustomer(i.customerId(),"operator"))))
                .hasMessage("outbox-unavailable");
        assertThat(new SettlementPreparationReads(jdbc,MAPPER).request(key)).isEmpty();
    }
    @Test void concurrentDuplicateCommandsHaveOneRequestAndOneOutboxSave() throws Exception {
        var i=seed();var key=UUID.randomUUID();var outbox=mock(OutboxEventRepository.class);var service=service(outbox);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            Runnable prepare=()->tx.executeWithoutResult(s->service.prepare(i.paymentId(),key,new AuthorizedCustomer(i.customerId(),"operator")));
            var first=java.util.concurrent.CompletableFuture.runAsync(prepare,pool);
            var second=java.util.concurrent.CompletableFuture.runAsync(prepare,pool);
            java.util.concurrent.CompletableFuture.allOf(first,second).orTimeout(20,java.util.concurrent.TimeUnit.SECONDS).join();
            assertThat(jdbc.queryForObject("select count(*) from payment_settlement_preparation where request_id=?",Integer.class,key)).isEqualTo(1);
            verify(outbox).save(any());
        } finally {pool.shutdownNow();}
    }
    @Test void changedRecipientCannotUsePreviouslyCapturedInstruction() throws Exception {
        var i=seed();var outbox=mock(OutboxEventRepository.class);var service=service(outbox);
        jdbc.update("update beneficiary set wallet_address=? where beneficiary_id=(select beneficiary_id from payment where payment_id=?)",
                address("f"),i.paymentId());
        assertThatThrownBy(()->tx.execute(s->service.prepare(i.paymentId(),UUID.randomUUID(),new AuthorizedCustomer(i.customerId(),"operator"))))
                .hasMessage("SETTLEMENT_OWNER_INSTRUCTION_CHANGED");
        verifyNoInteractions(outbox);
    }

    @Test void quoteDraftSurvivesJsonbAndCannotBeMutated() throws Exception {
        var i=seed(); var requestId=UUID.randomUUID(); var actor=new AuthorizedCustomer(i.customerId(),"operator");
        tx.executeWithoutResult(s->service(mock(OutboxEventRepository.class)).prepare(i.paymentId(),requestId,actor));
        var quote=SettlementQuoteFixtures.quote(i,requestId,UUID.randomUUID()); var store=new SettlementQuoteStore(jdbc);
        tx.executeWithoutResult(s->store.capture(quote,actor.actorId()));
        tx.executeWithoutResult(s->store.capture(quote,actor.actorId()));
        assertThat(store.find(quote.review().recommendationId())).contains(quote);
        assertThatThrownBy(()->jdbc.update("update payment_settlement_quote set actor_id='other' where review_id=?",quote.review().recommendationId()))
                .hasMessageContaining("SETTLEMENT_BINDING_IMMUTABLE");
        assertThatThrownBy(()->jdbc.update("delete from payment_settlement_quote where review_id=?",quote.review().recommendationId()))
                .hasMessageContaining("SETTLEMENT_BINDING_IMMUTABLE");
    }
    @Test void quoteCannotReferenceAnUnrelatedPreparation() throws Exception {
        var i=seed(); var quote=SettlementQuoteFixtures.quote(i,UUID.randomUUID(),UUID.randomUUID());
        var store=new SettlementQuoteStore(jdbc);
        assertThatThrownBy(()->tx.executeWithoutResult(s->store.capture(quote,"operator")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(store.find(quote.review().recommendationId())).isEmpty();
    }
    @Test void failedOwnerTransactionCannotRetainAQuote() throws Exception {
        var i=seed(); var requestId=UUID.randomUUID(); var actor=new AuthorizedCustomer(i.customerId(),"operator");
        tx.executeWithoutResult(s->service(mock(OutboxEventRepository.class)).prepare(i.paymentId(),requestId,actor));
        var quote=SettlementQuoteFixtures.quote(i,requestId,UUID.randomUUID()); var store=new SettlementQuoteStore(jdbc);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{store.capture(quote,actor.actorId());throw new IllegalStateException("owner-rollback");}))
                .hasMessage("owner-rollback");
        assertThat(store.find(quote.review().recommendationId())).isEmpty();
    }
}
