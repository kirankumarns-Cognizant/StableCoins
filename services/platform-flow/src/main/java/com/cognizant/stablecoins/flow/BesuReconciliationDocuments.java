package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Canonical report identity shared by the report writer and payment-owner verifier. */
public final class BesuReconciliationDocuments {
    private BesuReconciliationDocuments() {}
    public static ObjectNode document(ObjectMapper mapper,SettlementBinding.Execution e,BesuClosureEvidence.Comparison comparison) {
        ObjectNode d=mapper.valueToTree(comparison);
        d.put("executionDocument",SettlementBindingJson.write(e));
        d.put("executionFingerprint",SettlementBindingJson.fingerprint(e));return d;
    }
    public static String hash(ObjectMapper mapper,JsonNode document) {
        return FlowJson.fingerprint(mapper,BesuClosureEvidence.canonical(document));
    }
    public static UUID id(ObjectMapper mapper,JsonNode document) {
        return UUID.nameUUIDFromBytes((BesuClosureEvidence.KIND+":"+hash(mapper,document)).getBytes(StandardCharsets.UTF_8));
    }
}
