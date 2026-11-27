package com.cognizant.stablecoins.flow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
final class BesuClosureTestData {
    private BesuClosureTestData(){}
    static SettlementBinding.Execution execution(){
        var chain=new SettlementNetwork.Chain("besu-local",20261125,"0x"+"1".repeat(64),SettlementNetwork.Classification.PRIVATE_DEMO);
        var context=new SettlementNetwork.Context(new SettlementNetwork.Token(chain,"0x"+"2".repeat(40),"DEMOUSD",6,"0x"+"3".repeat(64)),new SettlementNetwork.NativeAsset(chain,"BESU_GAS",18),"0x"+"4".repeat(40),SettlementNetwork.FeeModel.EXECUTION_ONLY,"besu-v1");
        var now=Instant.parse("2026-11-26T10:00:00Z");
        var instruction=new SettlementBinding.Instruction(UUID.randomUUID(),1,UUID.randomUUID(),"PAY-BESU","CORR-BESU",context,"0x"+"5".repeat(40),new BigDecimal("10"),new BigDecimal("9.123456"),SettlementBinding.Outcome.TOKEN_WALLET,new BigDecimal("3"),now.plusSeconds(3600));
        return new SettlementBinding.Execution(UUID.randomUUID(),new SettlementBinding.Review(UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),1,"PRIVATE-PROVIDER",instruction,BigDecimal.ONE,new BigDecimal("0.1"),now,now.plusSeconds(900)));
    }
}
