package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.FlowContract;
import com.cognizant.stablecoins.flow.SettlementNetwork;
import java.math.BigDecimal;
import java.util.Objects;

/** Server-resolved identity and recipient accepted when the new instruction was created. */
public record ConfirmedSettlement(SettlementNetwork.Context context, BigDecimal tokenAmount, String recipient) {
    public ConfirmedSettlement {
        Objects.requireNonNull(context, "SETTLEMENT_CONTEXT_REQUIRED");
        tokenAmount = FlowContract.money(tokenAmount, context.token().decimals());
        recipient = SettlementNetwork.address(recipient);
        if (recipient.equals(context.treasury())) throw new IllegalArgumentException("SELF_TRANSFER_NOT_ALLOWED");
    }
}
