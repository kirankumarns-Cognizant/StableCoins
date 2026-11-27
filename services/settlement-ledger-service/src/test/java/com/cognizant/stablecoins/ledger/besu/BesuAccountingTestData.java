package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import java.math.*;
import java.time.Instant;
import java.util.UUID;

public final class BesuAccountingTestData {
    private BesuAccountingTestData() {}
    public static final String HASH="0x"+"a".repeat(64), BLOCK="0x"+"b".repeat(64);
    public static SettlementBinding.Execution execution(String paymentId) {
        var chain=new SettlementNetwork.Chain("besu-local",20261125,"0x"+"1".repeat(64),SettlementNetwork.Classification.PRIVATE_DEMO);
        var context=new SettlementNetwork.Context(new SettlementNetwork.Token(chain,"0x"+"2".repeat(40),"DEMOUSD",6,"0x"+"3".repeat(64)),
            new SettlementNetwork.NativeAsset(chain,"BESU_GAS",18),"0x"+"4".repeat(40),SettlementNetwork.FeeModel.EXECUTION_ONLY,"besu-v1");
        var now=Instant.parse("2026-11-26T10:00:00Z");
        var instruction=new SettlementBinding.Instruction(UUID.randomUUID(),1,UUID.randomUUID(),paymentId,"CORR-"+paymentId,context,
            "0x"+"5".repeat(40),new BigDecimal("10.00"),new BigDecimal("9.123456"),SettlementBinding.Outcome.TOKEN_WALLET,new BigDecimal("3"),now.plusSeconds(3600));
        return new SettlementBinding.Execution(UUID.randomUUID(),new SettlementBinding.Review(UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),1,
            "PRIVATE-PROVIDER",instruction,BigDecimal.ONE,new BigDecimal("0.1"),now,now.plusSeconds(900)));
    }
    public static BesuAccountingEvidence.Receipt receipt(boolean success,long gasPrice) {
        return new BesuAccountingEvidence.Receipt(HASH,BLOCK,101,success,BigInteger.valueOf(21000),BigInteger.valueOf(gasPrice),BigInteger.valueOf(21000).multiply(BigInteger.valueOf(gasPrice)));
    }
}
