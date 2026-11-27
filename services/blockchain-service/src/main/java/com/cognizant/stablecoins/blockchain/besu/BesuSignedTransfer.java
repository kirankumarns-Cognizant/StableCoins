package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import java.math.*;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

/** Durable signed bytes, never signing keys. Deliberately excludes payload from diagnostic output. */
final class BesuSignedTransfer {
    final SettlementBinding.Execution execution;
    final long nonce;
    final BigDecimal gasLimit, gasPrice;
    final String hash;
    private final byte[] payload;

    BesuSignedTransfer(SettlementBinding.Execution execution, long nonce, BigDecimal gasLimit,
                       BigDecimal gasPrice, byte[] payload) {
        try {
            var c=execution.context(); var i=execution.instruction();
            if (c.token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
                    || c.token().chain().chainId()!=BesuSettings.CHAIN_ID
                    || !"DEMOUSD".equals(c.token().symbol()) || c.token().decimals()!=6
                    || nonce<0 || nonce==Long.MAX_VALUE || payload==null || payload.length==0 || payload.length>4096)
                throw new IllegalArgumentException();
            var decoded=TransactionDecoder.decode(Numeric.toHexString(payload));
            String data="0xa9059cbb"+"0".repeat(24)+i.recipient().substring(2)
                    +Numeric.toHexStringNoPrefixZeroPadded(i.tokenAmount().movePointRight(6).toBigIntegerExact(),64);
            if (!(decoded instanceof SignedRawTransaction tx) || !tx.getType().isLegacy()
                    || !Long.valueOf(BesuSettings.CHAIN_ID).equals(tx.getChainId())
                    || !c.treasury().equalsIgnoreCase(tx.getFrom())
                    || !c.token().contract().equalsIgnoreCase(tx.getTo()) || tx.getValue().signum()!=0
                    || !tx.getNonce().equals(BigInteger.valueOf(nonce))
                    || !tx.getGasLimit().equals(gasLimit.toBigIntegerExact())
                    || !tx.getGasPrice().equals(gasPrice.toBigIntegerExact())
                    || gasLimit.signum()<=0 || gasLimit.compareTo(new BigDecimal("30000000"))>0
                    || gasPrice.signum()<0 || gasPrice.toBigIntegerExact().bitLength()>128
                    || gasLimit.multiply(gasPrice).movePointLeft(18).compareTo(execution.review().nativeFeeReserve())>0
                    || !Numeric.prependHexPrefix(tx.getData()).equalsIgnoreCase(data))
                throw new IllegalArgumentException();
            this.execution=execution; this.nonce=nonce; this.gasLimit=gasLimit; this.gasPrice=gasPrice;
            this.payload=payload.clone(); this.hash=Hash.sha3(Numeric.toHexString(this.payload));
        } catch (Exception ignored) { throw new IllegalArgumentException("BESU_SIGNED_EXECUTION_MISMATCH"); }
    }
    byte[] payload() { return payload.clone(); }
    @Override public String toString() { return "BesuSignedTransfer[hash="+hash+"]"; }
}
