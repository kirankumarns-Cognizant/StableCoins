package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.model.OutboxEvent;
import com.cognizant.stablecoins.payment.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import java.util.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.payment.settlement.BindingFixtures.*;

class SettlementPreparationServiceTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final OutboxEventRepository outbox=mock(OutboxEventRepository.class);
    final SettlementPreparationReads reads=mock(SettlementPreparationReads.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"),ZoneOffset.UTC);
    final SettlementPreparationService service=new SettlementPreparationService(jdbc,mapper,outbox,reads,clock);
    final SettlementBinding.Instruction i=instruction();
    final AuthorizedCustomer actor=new AuthorizedCustomer(i.customerId(),"verified-operator");
    final UUID key=UUID.randomUUID();
    final Map<UUID,SettlementPreparation.Request> requests=new HashMap<>();
    @BeforeEach void setup(){
        when(jdbc.queryForList(startsWith("select a.customer_id"),eq(i.paymentId())))
            .thenReturn(List.of(Map.of("customer_id",i.customerId())));
        when(reads.instruction(i.paymentId())).thenReturn(i);
        when(reads.request(any())).thenAnswer(c->Optional.ofNullable(requests.get(c.getArgument(0))));
        when(jdbc.update(startsWith("insert into payment_settlement_preparation"),any(Object[].class))).thenAnswer(c->{
            Object[] args=(Object[])c.getRawArguments()[1];
            var r=SettlementBindingJson.read((String)args[5],SettlementPreparation.Request.class);
            return requests.putIfAbsent(r.requestId(),r)==null?1:0;
        });
    }
    @Test void capturesCanonicalRequestAndPublishesOneReadOnlyCommandOnReplay() throws Exception {
        var first=service.prepare(i.paymentId(),key,actor);
        assertThat(service.prepare(i.paymentId(),key,actor)).isEqualTo(first);
        assertThat(first.instruction()).isEqualTo(i);
        var event=org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(event.capture());
        var json=mapper.readTree(event.getValue().getPayload());
        assertThat(json.path("eventType").asText()).isEqualTo(SettlementPreparation.EVENT);
        assertThat(json.path("eventVersion").asText()).isEqualTo("1.0");
        assertThat(json.path("paymentId").asText()).isEqualTo(i.paymentId());
        assertThat(json.path("correlationId").asText()).isEqualTo(i.correlationId());
        assertThat(event.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(SettlementBindingJson.read(json.path("payload").toString(),SettlementPreparation.Request.class)).isEqualTo(first);
    }
    @Test void differentCustomerCannotReadOrCreatePreparation(){
        var other=new AuthorizedCustomer(UUID.randomUUID(),"another-user");
        assertThatThrownBy(()->service.prepare(i.paymentId(),key,other)).hasMessageContaining("outside customer scope");
        assertThatThrownBy(()->service.get(i.paymentId(),key,other)).hasMessageContaining("outside customer scope");
        verifyNoInteractions(reads,outbox);
    }
    @Test void changedOwnerMaterialFailsBeforeAnyRequestOrEvent(){
        doThrow(new IllegalStateException("SETTLEMENT_OWNER_INSTRUCTION_CHANGED")).when(reads).requireInstruction(i);
        assertThatThrownBy(()->service.prepare(i.paymentId(),key,actor)).hasMessage("SETTLEMENT_OWNER_INSTRUCTION_CHANGED");
        verify(jdbc,never()).update(anyString(),any(Object[].class));verifyNoInteractions(outbox);
    }
    @Test void crossInstructionKeyReuseCannotCreateAnotherCommand(){
        requests.put(key,new SettlementPreparation.Request(key,changedRecipient(i),clock.instant()));
        assertThatThrownBy(()->service.prepare(i.paymentId(),key,actor)).hasMessageContaining("SETTLEMENT_PREPARATION_KEY_CONFLICT");
        verifyNoInteractions(outbox);
    }
    @Test void failedPersistenceDoesNotPublish(){
        when(jdbc.update(startsWith("insert into payment_settlement_preparation"),any(Object[].class)))
            .thenThrow(new IllegalStateException("storage-failed"));
        assertThatThrownBy(()->service.prepare(i.paymentId(),key,actor)).hasMessage("storage-failed");
        verifyNoInteractions(outbox);
    }
    @Test void observationStatusNeverBecomesReadyFromRawBalances(){
        var r=new SettlementPreparation.Request(key,i,clock.instant());requests.put(key,r);
        var view=service.get(i.paymentId(),key,actor);
        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.readinessGate()).isEqualTo("RESERVATION_INTEGRATION_PENDING");
        assertThat(view.evidence()).isNull();
    }

    @Test @SuppressWarnings({"unchecked","rawtypes"}) void recordedRawBalancesNeverGrantReadinessAndExpiredEvidenceIsLabelled() throws Exception {
        var r=new SettlementPreparation.Request(key,i,clock.instant());requests.put(key,r);
        var observation=new SettlementObservation(i,100,java.math.BigDecimal.TEN,java.math.BigDecimal.ONE,
                java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,
                new java.math.BigDecimal("0.000000000000000001"),clock.instant(),clock.instant().plusSeconds(15));
        var evidence=new SettlementPreparation.Evidence(r,observation);
        when(jdbc.query(startsWith("select document::text,fingerprint from besu_preparation_observation"),any(RowMapper.class),any(Object[].class)))
                .thenAnswer(c->{
                    var rs=mock(java.sql.ResultSet.class);
                    when(rs.getString(1)).thenReturn(SettlementBindingJson.write(evidence));
                    when(rs.getString(2)).thenReturn(SettlementBindingJson.fingerprint(evidence));
                    return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
                });
        assertThat(service.get(i.paymentId(),key,actor).status()).isEqualTo("OBSERVED_NOT_READY");
        var later=new SettlementPreparationService(jdbc,mapper,outbox,reads,Clock.offset(clock,Duration.ofSeconds(15)));
        assertThat(later.get(i.paymentId(),key,actor).status()).isEqualTo("EXPIRED");
        verifyNoInteractions(outbox);
    }

    @Test @SuppressWarnings({"unchecked","rawtypes"}) void availabilityIsBoundToOriginalEvidenceAndNeverEnablesExecution() throws Exception {
        var r=new SettlementPreparation.Request(key,i,clock.instant());requests.put(key,r);
        var o=new SettlementObservation(i,100,java.math.BigDecimal.TEN,java.math.BigDecimal.ONE,
                java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,
                new java.math.BigDecimal("0.000000000000000001"),clock.instant(),clock.instant().plusSeconds(15));
        var evidence=new SettlementPreparation.Evidence(r,o);
        var available=new SettlementAvailability(evidence,new java.math.BigDecimal("9.5"),java.math.BigDecimal.ZERO,clock.instant());
        var corrupt=new java.util.concurrent.atomic.AtomicBoolean(false);
        when(jdbc.query(startsWith("select document::text,fingerprint from besu_preparation_"),any(RowMapper.class),any(Object[].class)))
                .thenAnswer(c->{
                    boolean availability=((String)c.getArgument(0)).contains("besu_preparation_availability");
                    Object value=availability?available:evidence;
                    var rs=mock(java.sql.ResultSet.class);
                    when(rs.getString(1)).thenReturn(SettlementBindingJson.write(value));
                    when(rs.getString(2)).thenReturn(availability&&corrupt.get()?"0".repeat(64):SettlementBindingJson.fingerprint(value));
                    return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
                });
        var view=service.get(i.paymentId(),key,actor);
        assertThat(view.status()).isEqualTo("OBSERVED_NOT_READY");
        assertThat(view.readinessGate()).isEqualTo("RESERVATION_CLAIM_INTEGRATION_PENDING");
        assertThat(view.availability().tokenAvailable()).isEqualByComparingTo("0.5");
        assertThat(view.availability().capacitySufficient()).isFalse();
        var later=new SettlementPreparationService(jdbc,mapper,outbox,reads,Clock.offset(clock,Duration.ofSeconds(15)));
        assertThat(later.get(i.paymentId(),key,actor).status()).isEqualTo("EXPIRED");
        corrupt.set(true);
        assertThatThrownBy(()->service.get(i.paymentId(),key,actor)).hasMessage("SETTLEMENT_AVAILABILITY_INTEGRITY_FAILED");
        verifyNoInteractions(outbox);
    }
}
