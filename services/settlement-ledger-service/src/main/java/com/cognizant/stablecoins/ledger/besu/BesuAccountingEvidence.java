package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import java.math.BigInteger;
import java.util.Objects;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Ledger-owned provenance; canonical execution JSON retains full chain/genesis/contract identity. */
public record BesuAccountingEvidence(String executionDocument, String executionFingerprint,
        String fundingReference, Receipt receipt) {
    private static final ObjectMapper JSON=JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    public String json() {
        try { return JSON.writeValueAsString(this); }
        catch(Exception error) { throw new IllegalArgumentException("BESU_ACCOUNTING_EVIDENCE_INVALID"); }
    }
    public static BesuAccountingEvidence read(String document) {
        if(document==null || document.length()>65536) throw new IllegalArgumentException("BESU_ACCOUNTING_EVIDENCE_INVALID");
        try { return Objects.requireNonNull(JSON.readValue(document,BesuAccountingEvidence.class)); }
        catch(Exception error) { throw new IllegalArgumentException("BESU_ACCOUNTING_EVIDENCE_INVALID"); }
    }
    public BesuAccountingEvidence {
        var execution=SettlementBindingJson.read(executionDocument,SettlementBinding.Execution.class);
        requirePrivate(execution);
        if (!SettlementBindingJson.fingerprint(execution).equals(executionFingerprint)
                || fundingReference==null || !(fundingReference.matches("SIM-[A-Za-z0-9._:-]{1,96}") || SettlementFundingReads.reference(execution).equals(fundingReference)))
            throw new IllegalArgumentException("BESU_ACCOUNTING_BINDING_INVALID");
        executionDocument=SettlementBindingJson.write(execution);
    }
    public static BesuAccountingEvidence allocation(SettlementBinding.Execution execution,String fundingReference) {
        requirePrivate(execution);
        return new BesuAccountingEvidence(SettlementBindingJson.write(execution),
                SettlementBindingJson.fingerprint(execution),fundingReference,null);
    }
    public BesuAccountingEvidence withReceipt(Receipt value) {
        return new BesuAccountingEvidence(executionDocument,executionFingerprint,fundingReference,Objects.requireNonNull(value));
    }
    public static void requirePrivate(SettlementBinding.Execution execution) {
        Objects.requireNonNull(execution,"BESU_EXECUTION_REQUIRED");
        var c=execution.context();
        if (c.token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
                || !"DEMOUSD".equals(c.token().symbol()) || c.token().decimals()!=6
                || !"BESU_GAS".equals(c.nativeAsset().symbol()) || c.nativeAsset().decimals()!=18
                || c.feeModel()!=SettlementNetwork.FeeModel.EXECUTION_ONLY
                || execution.instruction().outcome()!=SettlementBinding.Outcome.TOKEN_WALLET)
            throw new IllegalArgumentException("BESU_ACCOUNTING_CONTEXT_INVALID");
    }
    public record Receipt(String transactionHash,String blockHash,long blockNumber,boolean success,
            BigInteger gasUsed,BigInteger gasPriceWei,BigInteger totalFeeWei) {
        public Receipt {
            transactionHash=SettlementNetwork.hash(transactionHash); blockHash=SettlementNetwork.hash(blockHash);
            if (blockNumber<1 || gasUsed==null || gasUsed.signum()<=0
                    || gasUsed.compareTo(BigInteger.valueOf(30_000_000))>0
                    || gasPriceWei==null || gasPriceWei.signum()<0 || totalFeeWei==null
                    || !gasUsed.multiply(gasPriceWei).equals(totalFeeWei))
                throw new IllegalArgumentException("BESU_ACCOUNTING_RECEIPT_INVALID");
        }
    }
}
