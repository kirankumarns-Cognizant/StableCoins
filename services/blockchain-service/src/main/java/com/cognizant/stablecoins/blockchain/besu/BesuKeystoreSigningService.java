package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.blockchain.service.WalletSigningService;
import com.cognizant.stablecoins.flow.SettlementNetwork;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import org.web3j.crypto.*;

/** Dedicated private-demo signer. Public signer chain restrictions remain unchanged. */
public final class BesuKeystoreSigningService implements WalletSigningService {
    private final Credentials credentials;
    public BesuKeystoreSigningService(Path keystore, Path passwordFile, String expected, String publicTreasury) {
        String address = SettlementNetwork.address(expected);
        if (publicTreasury != null && !publicTreasury.isBlank()
                && address.equals(SettlementNetwork.address(publicTreasury)))
            throw new IllegalArgumentException("BESU_PUBLIC_TREASURY_REUSE_REJECTED");
        Credentials loaded;
        try {
            secure(keystore, 1_048_576);
            secure(passwordFile, 16_384);
            String password = Files.readString(passwordFile, StandardCharsets.UTF_8);
            if (password.isEmpty()) throw new IllegalArgumentException();
            // Preserve exact characters. Web3j requires a String and retains credentials in memory.
            loaded = WalletUtils.loadCredentials(password, keystore.toFile());
        } catch (Exception ignored) { throw new IllegalStateException("BESU_KEYSTORE_UNLOCK_FAILED"); }
        if (!address.equalsIgnoreCase(loaded.getAddress())) throw new IllegalStateException("BESU_SIGNER_ADDRESS_MISMATCH");
        if (loaded.getAddress().equalsIgnoreCase(Credentials.create("1").getAddress()))
            throw new IllegalStateException("BESU_MOCK_SIGNER_REJECTED");
        credentials = loaded;
    }
    private static void secure(Path path, long maximum) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) == 0 || Files.size(path) > maximum)
            throw new IllegalArgumentException();
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (!Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE).containsAll(permissions)
                    || !permissions.contains(PosixFilePermission.OWNER_READ))
                throw new IllegalArgumentException();
        }
    }
    @Override public byte[] signTransaction(RawTransaction transaction, long chainId) {
        if (chainId != BesuSettings.CHAIN_ID) throw new IllegalArgumentException("BESU_PRIVATE_CHAIN_REQUIRED");
        if (transaction == null || !transaction.getType().isLegacy())
            throw new IllegalArgumentException("BESU_LEGACY_TRANSACTION_REQUIRED");
        return TransactionEncoder.signMessage(transaction, chainId, credentials);
    }
    @Override public String getWalletAddress() { return credentials.getAddress(); }
}
