package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.SettlementNetwork;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/** Server-owned, immutable private context; never populated from payment/model arguments. */
public record BesuSettings(SettlementNetwork.Context context, Set<String> validators,
                           Set<String> recipients, BigInteger minimumGasPrice,
                           Duration maximumHeadAge) {
    public static final long CHAIN_ID = 20261125L;
    public static final BigInteger SUPPLY = BigInteger.TEN.pow(12);
    public BesuSettings {
        if (context == null || context.token().chain().classification() != SettlementNetwork.Classification.PRIVATE_DEMO
                || context.token().chain().chainId() != CHAIN_ID
                || !"besu-local".equals(context.token().chain().networkId())
                || !"DEMOUSD".equals(context.token().symbol()) || context.token().decimals() != 6
                || context.feeModel() != SettlementNetwork.FeeModel.EXECUTION_ONLY)
            throw new IllegalArgumentException("BESU_PRIVATE_CONTEXT_REQUIRED");
        validators = normalize(validators);
        recipients = normalize(recipients);
        if (validators.size() != 4 || recipients.isEmpty() || recipients.contains(context.treasury()))
            throw new IllegalArgumentException("BESU_PARTICIPANTS_INVALID");
        if (minimumGasPrice == null || minimumGasPrice.signum() < 0 || minimumGasPrice.bitLength() > 128
                || maximumHeadAge == null || maximumHeadAge.compareTo(Duration.ofSeconds(2)) < 0
                || maximumHeadAge.compareTo(Duration.ofMinutes(2)) > 0)
            throw new IllegalArgumentException("BESU_FEE_OR_FRESHNESS_POLICY_INVALID");
    }
    private static Set<String> normalize(Set<String> values) {
        if (values == null) throw new IllegalArgumentException("BESU_PARTICIPANTS_REQUIRED");
        return values.stream().map(SettlementNetwork::address).collect(Collectors.toUnmodifiableSet());
    }
    public String recipient(String value) {
        String result = SettlementNetwork.address(value);
        if (!recipients.contains(result)) throw new IllegalArgumentException("BESU_RECIPIENT_NOT_ACCEPTED");
        return result;
    }
    public void token(String contract, int decimals) {
        if (!context.token().contract().equals(SettlementNetwork.address(contract)) || decimals != 6)
            throw new IllegalArgumentException("BESU_TOKEN_CONTEXT_MISMATCH");
    }
}
