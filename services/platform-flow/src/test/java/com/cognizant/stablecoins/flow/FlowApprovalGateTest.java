package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.*;
import com.cognizant.stablecoins.flow.FlowContract.Execution;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FlowApprovalGateTest {
 final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
 final JdbcTemplate jdbc = mock(JdbcTemplate.class);
 final FlowReadModels reads = spy(new FlowReadModels(jdbc, mapper));
 final FlowContract.Request request = new FlowContractTest().request();
 final UUID preparation = UUID.randomUUID();
 String wallet = request.recipientWallet();
 UUID snapshot = preparation;
 final Instant expiry = Instant.now().plusSeconds(600);
 final Execution execution = new Execution(UUID.randomUUID(), UUID.randomUUID(), preparation,
     request.paymentId(), request.correlationId(), "PREPARING", request, null, expiry);

 ObjectNode intent() throws Exception {
  return (ObjectNode) mapper.readTree("""
   {"intentId":"intent-1","customerId":"customer-1","confirmedBy":"operator",
    "instruction":{"maximumFee":5,"deliverBy":"2030-01-01T00:00:00Z",
      "allowedAlternatives":["USDC_WALLET","SIMULATED_BANK_USD"]},
    "policy":{"version":"v1","requiredApprovals":2,
      "allowedOutcomes":["USDC_WALLET","SIMULATED_BANK_USD"],
      "allowedProviderIds":["A","B"]}}
   """);
 }
 ObjectNode document(ObjectNode intent) {
  var doc = mapper.createObjectNode();
  doc.set("intent", intent);
  doc.putObject("instruction").put("recipientWallet", wallet);
  doc.putObject("evidence").put("snapshotId", snapshot.toString());
  return doc;
 }
 void gate(ObjectNode reviewed, ObjectNode current, String state, Instant reviewExpiry) throws Exception {
  var rs = mock(ResultSet.class);
  when(rs.getString(3)).thenReturn(document(reviewed).toString());
  when(rs.getString("current_intent")).thenReturn(current.toString());
  when(rs.getString(1)).thenReturn(state);
  when(rs.getTimestamp(2)).thenReturn(Timestamp.from(reviewExpiry));
  doAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)))
    .when(jdbc).query(contains("select r.state"), any(RowMapper.class),
      eq(execution.recommendationId()), eq(execution.paymentId()), eq(execution.correlationId()));
  // Isolate the approval gate; verify that successful approval still invokes the canonical request gate.
  doNothing().when(reads).requireRequested(request);
 }
 @Test void approvalAcceptsReorderedSetMembersAndStillChecksCanonicalRequest() throws Exception {
  var reviewed = intent(); var current = intent();
  ((ObjectNode) current.path("policy")).putArray("allowedOutcomes").add("SIMULATED_BANK_USD").add("USDC_WALLET");
  ((ObjectNode) current.path("policy")).putArray("allowedProviderIds").add("B").add("A");
  ((ObjectNode) current.path("instruction")).putArray("allowedAlternatives").add("SIMULATED_BANK_USD").add("USDC_WALLET");
  gate(reviewed, current, "APPROVED", expiry);
  reads.requireApproved(execution);
  verify(reads).requireRequested(request);
 }
 @Test void approvalRejectsChangedMembership() throws Exception {
  var current = intent(); ((ObjectNode) current.path("policy")).putArray("allowedProviderIds").add("A").add("C");
  gate(intent(), current, "APPROVED", expiry);
  assertThatThrownBy(() -> reads.requireApproved(execution)).hasMessage("CURRENT_APPROVAL_REQUIRED");
  verify(reads, never()).requireRequested(request);
 }
 @Test void approvalRejectsExpiredReview() throws Exception {
  gate(intent(), intent(), "APPROVED", Instant.now().minusSeconds(60));
  assertThatThrownBy(() -> reads.requireApproved(execution)).hasMessage("CURRENT_APPROVAL_REQUIRED");
 }
 @Test void approvalRejectsUnapprovedState() throws Exception {
  gate(intent(), intent(), "PROPOSED", expiry);
  assertThatThrownBy(() -> reads.requireApproved(execution)).hasMessage("CURRENT_APPROVAL_REQUIRED");
 }
 @Test void comparisonRetainsEveryOtherFieldAndOrderedArray() throws Exception {
  for (var field : List.of("maximumFee", "deliverBy")) {
   var current=intent(); ((ObjectNode) current.path("instruction")).put(field, "changed");
   assertThat(IntentSnapshotComparison.matches(intent(), current)).isFalse();
  }
  var reviewed=intent(); var current=intent();
  reviewed.putArray("orderedEvidence").add("one").add("two");
  current.putArray("orderedEvidence").add("two").add("one");
  assertThat(IntentSnapshotComparison.matches(reviewed,current)).isFalse();
  current=intent(); current.put("confirmedBy","another-operator");
  assertThat(IntentSnapshotComparison.matches(intent(),current)).isFalse();
  current=intent(); ((ObjectNode)current.path("policy")).put("requiredApprovals",1);
  assertThat(IntentSnapshotComparison.matches(intent(),current)).isFalse();
 }
 @Test void comparisonRejectsChangedMembersInEachSet() throws Exception {
  for (var pair : List.of(new String[]{"policy","allowedOutcomes"},new String[]{"policy","allowedProviderIds"},new String[]{"instruction","allowedAlternatives"})) {
   var current=intent(); ((ObjectNode)current.path(pair[0])).putArray(pair[1]).add("different");
   assertThat(IntentSnapshotComparison.matches(intent(),current)).isFalse();
  }
 }
 @Test void comparisonFailsClosedOnMalformedSets() throws Exception {
  for (var value : List.of("null", "{}", "[1]", "[\"A\",\"A\"]", "[\"\"]")) {
   var current=intent(); ((ObjectNode)current.path("policy")).set("allowedProviderIds",mapper.readTree(value));
   assertThat(IntentSnapshotComparison.matches(current,current)).isFalse();
  }
  var missing=intent(); ((ObjectNode)missing.path("policy")).remove("allowedOutcomes");
  assertThat(IntentSnapshotComparison.matches(missing,missing)).isFalse();
 }
 @Test void comparisonDoesNotMutateReviewedEvidence() throws Exception {
  var reviewed=intent(); var before=reviewed.deepCopy();
  assertThat(IntentSnapshotComparison.matches(reviewed,intent())).isTrue();
  assertThat(reviewed).isEqualTo(before);
 }

 @Test void approvalRejectsChangedRecipient() throws Exception {
  wallet="0x"+"2".repeat(40); gate(intent(),intent(),"APPROVED",expiry);
  assertThatThrownBy(()->reads.requireApproved(execution)).hasMessage("CURRENT_APPROVAL_REQUIRED");
 }
 @Test void approvalRejectsDifferentPreparation() throws Exception {
  snapshot=UUID.randomUUID(); gate(intent(),intent(),"APPROVED",expiry);
  assertThatThrownBy(()->reads.requireApproved(execution)).hasMessage("CURRENT_APPROVAL_REQUIRED");
 }
 @Test void approvalRejectsExpiredExecution() throws Exception {
  gate(intent(),intent(),"APPROVED",expiry);
  var expired=new Execution(execution.executionId(),execution.recommendationId(),preparation,
      request.paymentId(),request.correlationId(),"PREPARING",request,null,Instant.now().minusSeconds(60));
  assertThatThrownBy(()->reads.requireApproved(expired)).hasMessage("CURRENT_APPROVAL_REQUIRED");
 }
 @Test void approvalPreservesCanonicalRequestRejection() throws Exception {
  gate(intent(),intent(),"APPROVED",expiry);
  doThrow(new IllegalStateException("CANONICAL_PREPARATION_REQUIRED")).when(reads).requireRequested(request);
  assertThatThrownBy(()->reads.requireApproved(execution)).hasMessage("CANONICAL_PREPARATION_REQUIRED");
 }
 @Test void executedReviewRemainsAcceptedWhileCurrent() throws Exception {
  gate(intent(),intent(),"EXECUTED",expiry); reads.requireApproved(execution);
  verify(reads).requireRequested(request);
 }
}
