package com.cognizant.stablecoins.blockchain.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletUtils;

/** Local/Codespaces testnet signer. Never returns credentials or includes secrets in errors. */
public final class KeystoreTestnetSigningService implements WalletSigningService {
    private final Credentials credentials;

    public KeystoreTestnetSigningService(Path keystore, Path passwordFile, String expectedAddress) {
        if (expectedAddress == null || !expectedAddress.matches("(?i)0x[0-9a-f]{40}")
                || expectedAddress.matches("(?i)0x0{40}")) {
            throw new IllegalStateException("TESTNET_EXPECTED_WALLET_ADDRESS_REQUIRED");
        }
        Credentials loaded;
        try {
            if (!Files.isRegularFile(keystore) || !Files.isRegularFile(passwordFile)
                    || Files.size(keystore) > 1_048_576 || Files.size(passwordFile) > 16_384) {
                throw new IllegalArgumentException();
            }
            // Preserve every password character: no trim or newline removal.
            // web3j requires a String; this local signer retains decrypted credentials in JVM memory.
            String password = Files.readString(passwordFile, StandardCharsets.UTF_8);
            if (password.isEmpty()) throw new IllegalArgumentException();
            loaded = WalletUtils.loadCredentials(password, keystore.toFile());
        } catch (Exception failure) {
            // Do not attach the cause: library/file errors can contain paths or sensitive input.
            throw new IllegalStateException("TESTNET_KEYSTORE_UNLOCK_FAILED");
        }
        if (!loaded.getAddress().equalsIgnoreCase(expectedAddress)) {
            throw new IllegalStateException("TESTNET_SIGNER_ADDRESS_MISMATCH");
        }
        if (loaded.getAddress().equalsIgnoreCase("0x7e5f4552091a69125d5dfcb7b8c2659029395bdf")) {
            throw new IllegalStateException("TESTNET_KNOWN_MOCK_SIGNER_REJECTED");
        }
        credentials = loaded;
    }

    @Override
    public byte[] signTransaction(RawTransaction transaction, long chainId) {
        if (chainId != 84532L && chainId != 11155111L) {
            throw new IllegalArgumentException("PUBLIC_TESTNET_CHAIN_REQUIRED");
        }
        return TransactionEncoder.signMessage(transaction, chainId, credentials);
    }

    @Override
    public String getWalletAddress() {
        return credentials.getAddress();
    }
}
