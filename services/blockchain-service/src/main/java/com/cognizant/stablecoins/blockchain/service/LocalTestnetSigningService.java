package com.cognizant.stablecoins.blockchain.service;

import org.web3j.crypto.Credentials;

/**
 * Signs transactions using a local private key loaded from an environment variable.
 * SECURITY: The private key is NEVER stored in PostgreSQL, never logged, never in any event payload.
 * It is ONLY loaded from the BLOCKCHAIN_WALLET_PRIVATE_KEY environment variable.
 */
public class LocalTestnetSigningService implements WalletSigningService {

    private final Credentials credentials;

    public LocalTestnetSigningService(String privateKey) {
        // privateKey comes from env var — never from DB, never logged
        this.credentials = Credentials.create(privateKey);
    }

    @Override public byte[] signTransaction(org.web3j.crypto.RawTransaction transaction,long chainId) {
        return org.web3j.crypto.TransactionEncoder.signMessage(transaction,chainId,credentials);
    }

    @Override
    public String getWalletAddress() {
        return credentials.getAddress();
    }
}
