package com.cognizant.stablecoins.ledger.accounting;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.ledger.besu.*;
import com.cognizant.stablecoins.ledger.model.LedgerAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.math.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.ledger.besu.BesuAccountingTestData.*;

class BesuAccountingServiceTest {
    final AccountingFixture f=new AccountingFixture();
    final BesuAccountingStore store=mock(BesuAccountingStore.class);
    final BesuAccountingPolicy policy=new BesuAccountingPolicy(f.accounts);
    final BesuAccountingService service=new BesuAccountingService(f.ledger,f.policy,policy,store);
    final SettlementBinding.Execution e=execution("PAY-BESU");
    final AtomicReference<BesuAccountingStore.Acknowledgement> ack=new AtomicReference<>();
    @BeforeEach void setup() {
        for(var role:BesuAccountingPolicy.Role.values()) {
            var a=new LedgerAccount();a.setLedgerAccountId(UUID.randomUUID());a.setAccountCode(role.code);a.setAsset(role.asset);a.setAccountType(role.type);a.setActive(true);f.chart.put(role.code,a);
        }
        when(store.submission(e)).thenReturn(new BesuAccountingStore.Submission(HASH,"CONFIRMED",BigInteger.valueOf(25000),BigInteger.valueOf(3)));
        when(store.receipt(eq(e),any())).thenReturn(receipt(true,3));
        when(store.acknowledgement(e.executionId())).thenAnswer(i->Optional.ofNullable(ack.get()));
        doAnswer(i->{ack.set(i.getArgument(1));return null;}).when(store).acknowledge(eq(e),any());
    }
    void fund(){service.fundAndAllocate(e,"SIM-BESU-1");}
    void submitted(){fund();service.submitted(e);}
    @Test void allocationKeepsSimulatedUsdAndPrivateTokenSeparate() {
        var journals=service.fundAndAllocate(e,"SIM-BESU-1");assertThat(journals).hasSize(3);
        assertThat(journals).allMatch(j->j.getEvidence().mode()==PostingEvidence.Mode.SIMULATED && j.getEvidence().terms()==null);
        assertThat(f.lines).extracting(x->x.getAsset()).containsOnly("USD","DEMOUSD");
        assertThat(f.lines).filteredOn(x->x.getAsset().equals("DEMOUSD")).allMatch(x->x.getDebit().add(x.getCredit()).compareTo(e.instruction().tokenAmount())==0);
        assertThat(service.fundAndAllocate(e,"SIM-BESU-1")).isEqualTo(journals);assertThat(f.lines).hasSize(6);
    }
    @Test void confirmationAndActualWeiFeeAreReplaySafe() {
        submitted();var result=service.resolve(e);assertThat(result.confirmationJournal()).isNotNull();assertThat(result.feeJournal()).isNotNull();
        assertThat(service.resolve(e)).isEqualTo(result);assertThat(f.saved).hasSize(6);assertThat(f.lines).hasSize(12);
        assertThat(f.lines).filteredOn(x->x.getAsset().equals("BESU_GAS")).allMatch(x->x.getDebit().add(x.getCredit()).compareTo(new BigDecimal("0.000000000000063"))==0);
        verify(store).acknowledge(eq(e),any());
        assertThat(f.saved.values()).filteredOn(j->j.getEvidence().mode()==PostingEvidence.Mode.PRIVATE_DEMO)
            .allMatch(j->j.getEvidence().transactionHash().equals(HASH));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void completeZeroFeeIsAcknowledgedWithoutInventedExpense(boolean success) {
        submitted();when(store.receipt(eq(e),any())).thenReturn(receipt(success,0));
        var result=service.resolve(e);assertThat(result.feeJournal()).isNull();assertThat(result.confirmationJournal()!=null).isEqualTo(success);
        assertThat(result.evidence().receipt().totalFeeWei()).isZero();assertThat(service.resolve(e)).isEqualTo(result);
        assertThat(f.lines).noneMatch(x->x.getAsset().equals("BESU_GAS"));verify(store).acknowledge(eq(e),any());
    }
    @Test void revertedReceiptExpensesGasWithoutSuccessfulTokenDelivery() {
        submitted();when(store.receipt(eq(e),any())).thenReturn(receipt(false,3));var r=service.resolve(e);
        assertThat(r.confirmationJournal()).isNull();assertThat(r.feeJournal()).isNotNull();
        assertThat(f.saved.values()).noneMatch(j->j.getJournalType().equals("BLOCKCHAIN_CONFIRMATION"));
        assertThat(f.saved.values()).noneMatch(j->j.getJournalType().startsWith("REVERSAL"));
    }
    @Test void uncommittedFundingCannotCreateAllocation() {
        doThrow(new IllegalStateException("COMMITTED_BESU_FUNDING_REQUIRED")).when(store).requireFunded(e,"SIM-BESU-1");
        assertThatThrownBy(this::fund).hasMessage("COMMITTED_BESU_FUNDING_REQUIRED");assertThat(f.saved).isEmpty();
    }
    @Test void canonicalOwnerFundingReferenceSurvivesLedgerEvidence() {
        String reference=SettlementFundingReads.reference(e);
        var journals=service.fundAndAllocate(e,reference);
        assertThat(journals).hasSize(3).allMatch(j->reference.equals(j.getEvidence().privateSettlement().fundingReference()));
        var mapper=new ObjectMapper().findAndRegisterModules();
        var observed=journals.stream().map(j->new LifecycleReadModels.Journal(j.getJournalId(),j.getPaymentId(),j.getCorrelationId(),j.getJournalType(),j.getStatus(),j.getReference(),
            FlowJson.value(mapper,FlowJson.json(mapper,j.getEvidence()),com.fasterxml.jackson.databind.JsonNode.class),
            f.lines.stream().filter(x->x.getJournalId().equals(j.getJournalId())).map(x->{
                var account=f.chart.values().stream().filter(a->a.getLedgerAccountId().equals(x.getLedgerAccountId())).findFirst().orElseThrow();
                return new LifecycleReadModels.Entry(account.getAccountCode(),x.getAsset(),x.getDebit(),x.getCredit());
            }).toList())).toList();
        new SettlementFundingReads(mock(org.springframework.jdbc.core.JdbcTemplate.class),mapper,
            new BesuClosureEvidence.Policy(f.policy.version(),"110002","110001","110003")).validateAllocation(e,observed);
    }
    @Test void claimOnlyDoesNotCreateAnyAutomaticJournals() {verifyNoInteractions(store);assertThat(f.saved).isEmpty();}
    @Test void absentOwnerClaimRejectsBeforeAnyPosting() {
        doThrow(new IllegalStateException("CLAIM_MISSING")).when(store).requireClaimed(e);
        assertThatThrownBy(this::fund).hasMessage("CLAIM_MISSING");assertThat(f.saved).isEmpty();
    }
    @Test void missingAllocationCannotSubmit() {assertThatThrownBy(()->service.submitted(e)).hasMessageContaining("BESU_ALLOCATION_REQUIRED");assertThat(f.saved).isEmpty();}
    @Test void missingSubmissionCannotConfirm() {fund();assertThatThrownBy(()->service.resolve(e)).hasMessageContaining("BESU_PRIOR_JOURNAL_REQUIRED");assertThat(f.saved).hasSize(3);}
    @Test void uncertainReceiptCannotCreateConfirmationOrExpense() {
        submitted();doThrow(new IllegalStateException("BESU_LEDGER_OWNER_EVIDENCE_REQUIRED")).when(store).receipt(eq(e),any());
        assertThatThrownBy(()->service.resolve(e)).hasMessage("BESU_LEDGER_OWNER_EVIDENCE_REQUIRED");assertThat(f.saved).hasSize(4);verify(store,never()).acknowledge(any(),any());
    }
    @Test void changedFundingReplayFailsClosed() {fund();assertThatThrownBy(()->service.fundAndAllocate(e,"SIM-CHANGED")).hasMessageContaining("JOURNAL_IDEMPOTENCY_CONFLICT");assertThat(f.lines).hasSize(6);}
    @Test void partialFundingRequiresReview() {fund();f.saved.values().removeIf(j->j.getJournalType().equals("CONVERSION_USD"));assertThatThrownBy(this::fund).hasMessageContaining("BESU_PARTIAL_ALLOCATION_REVIEW_REQUIRED");}
    @Test void changedExecutionCannotReuseAllocation() {
        fund();var changed=new SettlementBinding.Execution(UUID.randomUUID(),e.review());
        assertThatThrownBy(()->service.submitted(changed)).hasMessageContaining("BESU_PRIOR_JOURNAL_MISMATCH");
    }
    @Test void revertedPriorJournalCannotAuthorizeNewMovement() {fund();f.saved.values().iterator().next().setStatus("REVERSED");assertThatThrownBy(()->service.submitted(e)).hasMessageContaining("BESU_PRIOR_JOURNAL_MISMATCH");}
    @Test void changedReceiptCannotReuseConfirmedJournal() {
        submitted();service.resolve(e);when(store.receipt(eq(e),any())).thenReturn(new BesuAccountingEvidence.Receipt(HASH,"0x"+"c".repeat(64),102,true,BigInteger.valueOf(21000),BigInteger.valueOf(3),BigInteger.valueOf(63000)));
        assertThatThrownBy(()->service.resolve(e)).hasMessageContaining("JOURNAL_IDEMPOTENCY_CONFLICT");
    }
    @Test void acknowledgementConflictFailsClosed() {
        submitted();ack.set(new BesuAccountingStore.Acknowledgement(BesuAccountingEvidence.allocation(e,"SIM-BESU-1").withReceipt(receipt(true,3)),UUID.randomUUID(),UUID.randomUUID()));
        assertThatThrownBy(()->service.resolve(e)).hasMessageContaining("BESU_RECEIPT_ACCOUNTING_CONFLICT");
    }
    @Test void publicServiceCannotUsePrivateMode() {
        var c=f.context(PostingEvidence.Outcome.USDC_WALLET);f.service.fundAndConvert(c);
        assertThatThrownBy(()->f.service.submitted(c,HASH,PostingEvidence.Mode.PRIVATE_DEMO)).hasMessageContaining("CHAIN_EVIDENCE_MODE_REQUIRED");
    }
    @Test void malformedAndIncompleteReceiptsAreRejected() {
        assertThatThrownBy(()->new BesuAccountingEvidence.Receipt(HASH,BLOCK,101,true,BigInteger.valueOf(21000),BigInteger.valueOf(3),BigInteger.ONE)).hasMessage("BESU_ACCOUNTING_RECEIPT_INVALID");
        assertThatThrownBy(()->new BesuAccountingEvidence.Receipt(HASH,BLOCK,0,true,BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO)).hasMessage("BESU_ACCOUNTING_RECEIPT_INVALID");
    }
    @Test void provenanceTamperingAndNonSimulatedFundingReferenceAreRejected() {
        var b=BesuAccountingEvidence.allocation(e,"SIM-1");
        assertThatThrownBy(()->new BesuAccountingEvidence(b.executionDocument(),"a".repeat(64),b.fundingReference(),null)).hasMessage("BESU_ACCOUNTING_BINDING_INVALID");
        assertThatThrownBy(()->service.fundAndAllocate(e,"REAL-BANK-1")).hasMessage("BESU_ACCOUNTING_BINDING_INVALID");assertThat(f.saved).isEmpty();
    }
    @Test void legacyEvidenceSerializationRemainsByteForByteCompatible() throws Exception {
        var mapper=new ObjectMapper();var evidence=new PostingEvidence(PostingEvidence.Mode.MOCK,"adr015-v1",null,HASH,null,null);
        String expected="{\"mode\":\"MOCK\",\"policyVersion\":\"adr015-v1\",\"terms\":null,\"transactionHash\":\""+HASH+"\",\"payoutReference\":null,\"receiptFee\":null}";
        assertThat(mapper.writeValueAsString(evidence)).isEqualTo(expected);
        assertThat(mapper.readValue(expected,PostingEvidence.class)).isEqualTo(evidence);
        submitted();service.resolve(e);
        assertThat(BesuAccountingEvidence.read(ack.get().evidence().json())).isEqualTo(ack.get().evidence());
        for(var j:f.saved.values()) assertThat(mapper.readValue(mapper.writeValueAsString(j.getEvidence()),PostingEvidence.class)).isEqualTo(j.getEvidence());
    }
    @Test void privateComponentsAreDisabledByDefault() {
        new ApplicationContextRunner().withUserConfiguration(BesuAccountingService.class,BesuAccountingPolicy.class,BesuAccountingStore.class,BesuFundingHandler.class)
            .run(c->{assertThat(c).doesNotHaveBean(BesuAccountingService.class);assertThat(c).doesNotHaveBean(BesuAccountingPolicy.class);assertThat(c).doesNotHaveBean(BesuAccountingStore.class);assertThat(c).doesNotHaveBean(BesuFundingHandler.class);});
    }
    @ParameterizedTest @ValueSource(strings={"missing","inactive","asset","type"})
    void privatePolicyRejectsInvalidChart(String defect) {
        var a=f.chart.get("150001");
        switch(defect){case "missing"->f.chart.remove("150001");case "inactive"->a.setActive(false);case "asset"->a.setAsset("USDC");case "type"->a.setAccountType("LIABILITY");}
        assertThatThrownBy(policy::afterSingletonsInstantiated).hasMessageContaining("BESU_ACCOUNTING_POLICY_INVALID");
    }
    @Test void genericPosterRejectsUnscopedPrivateAssetsAndExcessTokenPrecision() {
        var request=new com.cognizant.stablecoins.ledger.api.dto.PostJournalRequest();
        request.setPaymentId(e.instruction().paymentId());request.setCorrelationId(e.instruction().correlationId());request.setJournalType("PRIVATE_TEST");
        request.setEntries(List.of(policy.line(BesuAccountingPolicy.Role.TREASURY,BigDecimal.ONE,true),policy.line(BesuAccountingPolicy.Role.ALLOCATION,BigDecimal.ONE,false)));
        assertThatThrownBy(()->f.ledger.postJournal(request)).hasMessageContaining("PRIVATE_ASSET_PROVENANCE_REQUIRED");
        request.setEvidence(new PostingEvidence(PostingEvidence.Mode.SIMULATED,"test",null,null,null,null,BesuAccountingEvidence.allocation(e,"SIM-1")));
        request.getEntries().getFirst().setDebit(new BigDecimal("1.0000001"));request.getEntries().getLast().setCredit(new BigDecimal("1.0000001"));
        assertThatThrownBy(()->f.ledger.postJournal(request)).hasMessageContaining("AMOUNT_PRECISION_EXCEEDED");
        request.setPaymentId("PAY-OTHER");
        assertThatThrownBy(()->f.ledger.postJournal(request)).hasMessageContaining("PRIVATE_ASSET_PAYMENT_SCOPE_MISMATCH");assertThat(f.saved).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"public","token","decimals"})
    void privateAccountingCannotRelabelPublicOrUnsupportedAssets(String defect) {
        var original=e.context();var chain=original.token().chain();
        if(defect.equals("public")) chain=new SettlementNetwork.Chain("base-sepolia",84532,chain.genesisHash(),SettlementNetwork.Classification.PUBLIC_TESTNET);
        var context=new SettlementNetwork.Context(new SettlementNetwork.Token(chain,original.token().contract(),defect.equals("token")?"OTHER":"DEMOUSD",defect.equals("decimals")?7:6,original.token().codeHash()),
            new SettlementNetwork.NativeAsset(chain,defect.equals("public")?"ETH":"BESU_GAS",18),original.treasury(),
            defect.equals("public")?SettlementNetwork.FeeModel.BASE_L1_AND_EXECUTION:SettlementNetwork.FeeModel.EXECUTION_ONLY,original.policyVersion());
        var i=e.instruction();var instruction=new SettlementBinding.Instruction(i.intentId(),i.intentVersion(),i.customerId(),i.paymentId(),i.correlationId(),context,i.recipient(),i.sourceUsd(),i.tokenAmount(),i.outcome(),i.maximumFeeUsd(),i.deliverBy());
        var r=e.review();var review=new SettlementBinding.Review(r.recommendationId(),r.version(),r.preparationId(),r.quoteId(),r.quoteVersion(),r.providerId(),instruction,r.feeUsd(),r.nativeFeeReserve(),r.assessedAt(),r.expiresAt());
        assertThatThrownBy(()->service.fundAndAllocate(new SettlementBinding.Execution(e.executionId(),review),"SIM-1")).hasMessage("BESU_ACCOUNTING_CONTEXT_INVALID");
        assertThat(f.saved).isEmpty();verifyNoInteractions(store);
    }

    @ParameterizedTest @ValueSource(strings={"success-positive","success-zero","revert-positive","revert-zero","canonical-success-positive","canonical-success-zero","canonical-revert-positive","canonical-revert-zero"})
    void actualLedgerOutputAgreesWithSharedReconciliationContract(String scenario)throws Exception {
        boolean success=scenario.contains("success");long price=scenario.endsWith("zero")?0:3;
        when(store.submission(e)).thenReturn(new BesuAccountingStore.Submission(HASH,success?"CONFIRMED":"REVERTED",BigInteger.valueOf(25000),BigInteger.valueOf(price)));
        var actualReceipt=receipt(success,price);when(store.receipt(eq(e),any())).thenReturn(actualReceipt);
        if(scenario.startsWith("canonical")){service.fundAndAllocate(e,SettlementFundingReads.reference(e));service.submitted(e);}else submitted();
        var acknowledgement=service.resolve(e);var mapper=new ObjectMapper().findAndRegisterModules();
        var journals=f.saved.values().stream().map(j->new LifecycleReadModels.Journal(j.getJournalId(),j.getPaymentId(),j.getCorrelationId(),j.getJournalType(),j.getStatus(),j.getReference(),
            FlowJson.value(mapper,FlowJson.json(mapper,j.getEvidence()),com.fasterxml.jackson.databind.JsonNode.class),
            f.lines.stream().filter(x->x.getJournalId().equals(j.getJournalId())).map(x->{
                var account=f.chart.values().stream().filter(a->a.getLedgerAccountId().equals(x.getLedgerAccountId())).findFirst().orElseThrow();
                return new LifecycleReadModels.Entry(account.getAccountCode(),x.getAsset(),x.getDebit(),x.getCredit());
            }).toList())).toList();
        var accounts=f.chart.values().stream().map(a->new BesuClosureEvidence.Account(a.getAccountCode(),a.getAsset(),a.getAccountType())).toList();
        var i=e.instruction();
        var snapshot=new BesuClosureEvidence.Snapshot(e,new BesuClosureEvidence.Payment(i.paymentId(),i.correlationId(),i.customerId(),i.sourceUsd(),"USD"),
            new BesuClosureEvidence.Submission(e,SettlementBindingJson.fingerprint(e),e.context().nonceScope(),HASH,success?"CONFIRMED":"REVERTED",BigInteger.valueOf(25000),BigInteger.valueOf(price)),
            new BesuClosureEvidence.Receipt(e.context().nonceScope(),HASH,BLOCK,101,success,actualReceipt.gasUsed(),actualReceipt.gasPriceWei(),actualReceipt.totalFeeWei(),"QBFT_COMMITTED"),
            new BesuClosureEvidence.Acknowledgement(e.executionId(),i.paymentId(),i.correlationId(),mapper.readTree(acknowledgement.evidence().json()),acknowledgement.confirmationJournal(),acknowledgement.feeJournal()),journals,accounts,false);
        var result=BesuClosureEvidence.compare(mapper,new BesuClosureEvidence.Policy(f.policy.version(),"110002","110001","110003"),snapshot);
        if(success){assertThat(result.findings()).isEmpty();assertThat(result.status()).isEqualTo("MATCHED");}
        else {assertThat(result.findings()).containsExactly("TRANSFER_REVERTED_REQUIRES_REVIEW");assertThat(result.status()).isEqualTo("MISMATCH");}
    }

    @ParameterizedTest @ValueSource(strings={"success-positive","success-zero","revert-positive","revert-zero"})
    @SuppressWarnings("unchecked")
    void accountingWorkerRestartReusesExactJournalsAndReceiptAcknowledgement(String scenario) throws Exception {
        boolean success=scenario.startsWith("success");long price=scenario.endsWith("zero")?0:3;
        var status=new AtomicReference<>("UNKNOWN");var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(store.submission(e)).thenAnswer(i->new BesuAccountingStore.Submission(HASH,status.get(),BigInteger.valueOf(25000),BigInteger.valueOf(price)));
        when(store.receipt(eq(e),any())).thenReturn(receipt(success,price));
        doAnswer(call->{
            org.springframework.jdbc.core.RowMapper<Object> mapper=call.getArgument(1);var rs=mock(java.sql.ResultSet.class);
            when(rs.getObject(1,UUID.class)).thenReturn(e.executionId());when(rs.getString(2)).thenReturn(SettlementBindingJson.write(e));
            when(rs.getString(3)).thenReturn(SettlementBindingJson.fingerprint(e));when(rs.getString(4)).thenReturn(status.get());
            return List.of(mapper.mapRow(rs,0));
        }).when(jdbc).query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),isNull(),isNull(),eq(10));
        service.fundAndAllocate(e,SettlementFundingReads.reference(e));
        new BesuAccountingWorker(jdbc,service).poll();assertThat(f.saved).hasSize(4);assertThat(ack.get()).isNull();
        status.set(success?"CONFIRMED":"REVERTED");new BesuAccountingWorker(jdbc,service).poll();
        var first=ack.get();assertThat(first).isNotNull();var journalIds=f.saved.values().stream().map(j->j.getJournalId()).toList();
        // Replaying a stale discovery after another worker commits must also be harmless.
        new BesuAccountingWorker(jdbc,service).poll();assertThat(ack.get()).isEqualTo(first);
        assertThat(f.saved.values()).extracting(j->j.getJournalId()).containsExactlyElementsOf(journalIds);
        assertThat(first.confirmationJournal()!=null).isEqualTo(success);assertThat(first.feeJournal()!=null).isEqualTo(price>0);
        assertThat(f.saved).hasSize(4+(success?1:0)+(price>0?1:0));verify(store).acknowledge(eq(e),any());
    }

}
