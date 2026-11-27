package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.FlowContract;
import com.cognizant.stablecoins.flow.SettlementNetwork;
import java.math.BigDecimal;

/** Customer accepts identifiers and quantity, never RPC URLs or signing paths. */
public record SettlementSelection(String networkId, String tokenContract, BigDecimal tokenAmount) {
    public SettlementSelection {
        FlowContract.identifier(networkId, 50);
        tokenContract = SettlementNetwork.address(tokenContract);
        tokenAmount = FlowContract.money(tokenAmount, 18);
    }
    public String fingerprintMaterial() {
        return networkId + "|" + tokenContract + "|" + tokenAmount.toPlainString();
    }
}
