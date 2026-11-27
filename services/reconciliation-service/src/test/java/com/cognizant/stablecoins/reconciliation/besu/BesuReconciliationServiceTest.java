package com.cognizant.stablecoins.reconciliation.besu;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.reconciliation.flow.ReconciliationService.Report;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuReconciliationServiceTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final BesuClosureEvidence checker=mock(BesuClosureEvidence.class);
    final SettlementExecutionReads claims=mock(SettlementExecutionReads.class);
    final UUID reportId=UUID.randomUUID();
    SettlementBinding.Execution e;BesuReconciliationService service;ObjectNode stored;String status;String hash;UUID storedId;
    @BeforeEach @SuppressWarnings("unchecked") void setup(){
        var chain=new SettlementNetwork.Chain("besu-local",20261125,"0x"+"1".repeat(64),SettlementNetwork.Classification.PRIVATE_DEMO);
        var context=new SettlementNetwork.Context(new SettlementNetwork.Token(chain,"0x"+"2".repeat(40),"DEMOUSD",6,"0x"+"3".repeat(64)),new SettlementNetwork.NativeAsset(chain,"BESU_GAS",18),"0x"+"4".repeat(40),SettlementNetwork.FeeModel.EXECUTION_ONLY,"besu-v1");
        var now=Instant.parse("2026-11-26T10:00:00Z");
        var i=new SettlementBinding.Instruction(UUID.randomUUID(),1,UUID.randomUUID(),"PAY-BESU","CORR-BESU",context,"0x"+"5".repeat(40),BigDecimal.TEN,BigDecimal.TEN,SettlementBinding.Outcome.TOKEN_WALLET,BigDecimal.ONE,now.plusSeconds(3600));
        e=new SettlementBinding.Execution(UUID.randomUUID(),new SettlementBinding.Review(UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),1,"PRIVATE-PROVIDER",i,BigDecimal.ONE,BigDecimal.ZERO,now,now.plusSeconds(900)));
        service=new BesuReconciliationService(jdbc,mapper,checker,claims);
        when(jdbc.query(anyString(),any(RowMapper.class),any(UUID.class))).thenAnswer(call->stored==null || !call.getArgument(2).equals(storedId)?List.of():List.of(new Report(storedId,e.executionId(),i.paymentId(),i.correlationId(),status,hash,stored,now)));
        when(jdbc.update(startsWith("insert into reconciliation("),any(Object[].class))).thenAnswer(call->{
            Object[] args=call.getArguments();storedId=(UUID)args[1];status=(String)args[5];hash=(String)args[6];stored=(ObjectNode)mapper.readTree((String)args[7]);return 1;
        });
        comparison("MATCHED",List.of());
    }
    void comparison(String status,List<String> findings){when(checker.compare(any())).thenReturn(new BesuClosureEvidence.Comparison(BesuClosureEvidence.KIND,BesuClosureEvidence.GATE,new BesuClosureEvidence.Policy("adr015-v1","110002","110001","110003"),mapper.createObjectNode(),findings,status));}
    @Test void recordsComparisonWithoutOwnerStateWrites(){assertThat(service.run(reportId,e).status()).isEqualTo("MATCHED");verify(claims).requireClaimed(e);verify(checker).capture(e);assertThat(stored.path("closureGate").asText()).isEqualTo(BesuClosureEvidence.GATE);verify(jdbc,never()).update(startsWith("update payment"),any(Object[].class));}
    @Test void identicalReportKeyReturnsOriginalWithoutRecapturing(){service.run(reportId,e);service.run(reportId,e);verify(checker,times(1)).capture(e);verify(claims,times(2)).requireClaimed(e);}
    @Test void missingEvidenceWritesPendingFinding(){comparison("PENDING",List.of("MISSING_BESU_RECEIPT"));assertThat(service.run(reportId,e).status()).isEqualTo("PENDING");verify(jdbc).update("insert into reconciliation_detail(reconciliation_id,finding) values (?,?)",reportId,"MISSING_BESU_RECEIPT");}
    @Test void revertedOutcomeWritesMismatch(){comparison("MISMATCH",List.of("TRANSFER_REVERTED_REQUIRES_REVIEW"));assertThat(service.run(reportId,e).status()).isEqualTo("MISMATCH");}
    @Test void unclaimedExecutionCannotReadOrWriteComparison(){doThrow(new IllegalStateException("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED")).when(claims).requireClaimed(e);assertThatThrownBy(()->service.run(reportId,e)).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");verifyNoInteractions(checker,jdbc);}
    @Test void keyCannotReplayWithAlteredExecutionProjection(){service.run(reportId,e);stored.put("executionFingerprint","a".repeat(64));assertThatThrownBy(()->service.run(reportId,e)).hasMessage("BESU_RECONCILIATION_KEY_OR_EVIDENCE_CONFLICT");}
    @Test void storedStatusTamperingIsRejected(){service.run(reportId,e);stored.put("status","MISMATCH");assertThatThrownBy(()->service.run(reportId,e)).hasMessage("BESU_RECONCILIATION_KEY_OR_EVIDENCE_CONFLICT");}
    @Test void storedSourceTamperingIsRejected(){service.run(reportId,e);stored.withObject("sources").put("altered",true);assertThatThrownBy(()->service.run(reportId,e)).hasMessage("BESU_RECONCILIATION_KEY_OR_EVIDENCE_CONFLICT");}
    @Test void defaultApplicationDoesNotCreatePrivateReconciliationBean(){new ApplicationContextRunner().withUserConfiguration(BesuReconciliationService.class).run(c->assertThat(c).doesNotHaveBean(BesuReconciliationService.class));}
    @Test void explicitFalseAlsoLeavesPrivateReconciliationDisabled(){new ApplicationContextRunner().withUserConfiguration(BesuReconciliationService.class).withPropertyValues("app.besu.reconciliation.enabled=false").run(c->assertThat(c).doesNotHaveBean(BesuReconciliationService.class));}
    @Test void currentEvidenceRetryUsesStableReportIdentityAndRecapturesSources() {
        var first=service.runCurrent(e);assertThat(service.runCurrent(e)).isEqualTo(first);
        verify(checker,times(2)).capture(e);verify(jdbc,times(1)).update(startsWith("insert into reconciliation("),any(Object[].class));
        assertThat(first.reconciliationId()).isEqualTo(BesuReconciliationDocuments.id(mapper,stored));
    }
    @Test void changedCurrentSourcesCreateNewReportInsteadOfReusingOldResult() {
        var first=service.runCurrent(e);comparison("PENDING",List.of("MISSING_NETWORK_FEE"));var next=service.runCurrent(e);
        assertThat(next.reconciliationId()).isNotEqualTo(first.reconciliationId());assertThat(next.status()).isEqualTo("PENDING");
        verify(jdbc,times(2)).update(startsWith("insert into reconciliation("),any(Object[].class));
    }

}
