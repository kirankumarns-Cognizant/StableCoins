package com.cognizant.stablecoins.blockchain.service;
import org.web3j.crypto.RawTransaction;
/** Protected signer boundary: callers receive a public address or signed bytes, never credentials. */
public interface WalletSigningService {
    String getWalletAddress();
    byte[] signTransaction(RawTransaction transaction,long chainId);
}
