package com.cognizant.stablecoins.flow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static com.cognizant.stablecoins.flow.BesuClosureEvidence.*;
import static com.cognizant.stablecoins.flow.LifecycleReadModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuRevertEvidenceTest {
    BesuClosureEvidenceTest f;BesuClosureEvidence checker;
    @BeforeEach void setup(){f=new BesuClosureEvidenceTest();f.setup();f.fixture(false,7);checker=new BesuClosureEvidence(mock(JdbcTemplate.class),f.mapper,new MockEnvironment());}
    BesuRevertEvidence.Proof check(){return BesuRevertEvidence.validate(f.mapper,checker,f.s);}
    Journal release(){var j=check().submission();return new Journal(UUID.randomUUID(),j.paymentId(),j.correlationId(),BesuRevertEvidence.RELEASE,"POSTED",BesuRevertEvidence.reference(j.journalId()),j.evidence(),j.entries().stream().map(x->new Entry(x.accountCode(),x.asset(),x.credit(),x.debit())).toList());}
    @Test void conclusiveRevertIncludesActualGasButNeverDelivery(){var p=check();assertThat(p.released()).isFalse();assertThat(p.sources().receipt().success()).isFalse();assertThat(p.sources().journals()).noneMatch(j->j.type().equals("BLOCKCHAIN_CONFIRMATION"));}
    @Test void exactReleaseRetainsSourceIdentityAndOriginalJournal(){var before=check();var js=new ArrayList<>(f.s.journals());js.add(release());f.journals(js);var after=check();assertThat(after.released()).isTrue();assertThat(after.sourceHash()).isEqualTo(before.sourceHash());assertThat(after.submission()).isEqualTo(before.submission());assertThat(f.check().status()).isEqualTo("MISMATCH");}
    @Test void completeZeroGasRevertIsRecoverableWithoutFabricatedFee(){f.fixture(false,0);assertThat(check().sources().receipt().totalFeeWei()).isZero();}
    @Test void successfulTransferCannotBeCompensated(){f.fixture(true,7);assertThatThrownBy(this::check).hasMessage("BESU_CONCLUSIVE_REVERT_REQUIRED");}
    @ParameterizedTest @ValueSource(strings={"FUNDING","CONVERSION_USD","CONVERSION_DEMOUSD","BLOCKCHAIN_SUBMISSION","NETWORK_FEE"})
    void missingAccountingNeverCreatesRecoveryAuthority(String type){f.journals(f.s.journals().stream().filter(j->!type.equals(j.type())).toList());assertThatThrownBy(this::check).hasMessage("BESU_CONCLUSIVE_REVERT_REQUIRED");}
    @Test void unknownOutcomeCannotReleaseBalances(){var s=f.s;var sub=s.submission();f.s=f.snapshot(s.payment(),new Submission(sub.execution(),sub.fingerprint(),sub.scope(),sub.hash(),"UNKNOWN",sub.gasLimit(),sub.gasPrice()),null,null,s.journals().subList(0,4),s.accounts(),false);assertThatThrownBy(this::check).hasMessage("BESU_CONCLUSIVE_REVERT_REQUIRED");}
    @ParameterizedTest @ValueSource(strings={"reference","payment","correlation","status","evidence","entries","duplicate"})
    void releaseMustExactlyReverseOnlyTheOriginalTransitMovement(String field){
        var r=release();var js=new ArrayList<>(f.s.journals());
        var n=r.evidence().deepCopy();if(field.equals("evidence"))((com.fasterxml.jackson.databind.node.ObjectNode)n).put("mode","SIMULATED");
        js.add(new Journal(r.journalId(),field.equals("payment")?"OTHER":r.paymentId(),field.equals("correlation")?"OTHER":r.correlationId(),r.type(),field.equals("status")?"REVERSED":r.status(),field.equals("reference")?"OTHER":r.reference(),n,field.equals("entries")?check().submission().entries():r.entries()));
        if(field.equals("duplicate"))js.add(r);f.journals(js);assertThatThrownBy(this::check).hasMessage("BESU_REVERT_RELEASE_CONFLICT");
    }
    @Test void unexpectedAdditionalUsdRefundIsRejected(){var js=new ArrayList<>(f.s.journals());var j=js.getFirst();js.add(new Journal(UUID.randomUUID(),j.paymentId(),j.correlationId(),"USD_REFUND","POSTED",j.reference(),j.evidence(),j.entries()));f.journals(js);assertThatThrownBy(this::check).hasMessage("BESU_CONCLUSIVE_REVERT_REQUIRED");}
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8,9})
    void authorizationMustMatchEveryDurableSourceField(int changed) throws Exception {
        var jdbc=mock(JdbcTemplate.class);var rs=mock(java.sql.ResultSet.class);var proof=check();var e=f.e;
        var values=List.of("approver",proof.sourceHash(),BesuRevertEvidence.ACTION,SettlementBindingJson.fingerprint(e),SettlementBindingJson.write(e),proof.sourceHash(),proof.sources().receipt().transactionHash(),"FAILED","operator");
        for(int n=1;n<=9;n++)when(rs.getString(n)).thenReturn(n==changed?"forged":values.get(n-1));
        if(changed==1)when(rs.getString(1)).thenReturn("");
        if(changed==9)when(rs.getString(9)).thenReturn("approver");
        doAnswer(call->{org.springframework.jdbc.core.RowMapper<?> row=call.getArgument(1);return List.of(row.mapRow(rs,0));})
            .when(jdbc).query(anyString(),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<BesuRevertEvidence.Authorization>>any(),eq(e.executionId()));
        var reads=new BesuRevertEvidence(jdbc,f.mapper,checker,mock(SettlementFundingReads.class));
        assertThatThrownBy(()->reads.authorization(e,proof)).isInstanceOf(RuntimeException.class);
    }
    @Test void noPersistedAuthorizationMeansNoCompensation(){var jdbc=mock(JdbcTemplate.class);var reads=new BesuRevertEvidence(jdbc,f.mapper,checker,mock(SettlementFundingReads.class));assertThatThrownBy(()->reads.authorization(f.e,check())).hasMessage("BESU_COMPENSATION_AUTHORIZATION_REQUIRED");}

}
