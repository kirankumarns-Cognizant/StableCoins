package com.cognizant.stablecoins.tools.wallets;

import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.WalletUtils;
import org.web3j.crypto.exception.CipherException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;

/**
 * Core wallet-generation logic for the StableCoinsDemo developer utility.
 *
 * <p>Generates cryptographically secure EVM key pairs using web3j's
 * {@link Keys#createEcKeyPair()}, encrypts each with Ethereum Keystore V3
 * (AES-128-CTR + scrypt via {@link WalletUtils#generateWalletFile}), and
 * writes named JSON files to the specified directory.
 *
 * <p><strong>Security contract:</strong>
 * <ul>
 *   <li>Private keys are created in memory, used immediately to produce the encrypted
 *       keystore, and are never returned to callers.</li>
 *   <li>Passwords supplied as {@code char[]} are cleared (zeroed) after use to minimise
 *       the window in which they reside in memory.  Note: {@link ECKeyPair} uses
 *       {@code BigInteger} which is immutable and cannot be explicitly zeroed — this is
 *       a known Java limitation.</li>
 *   <li>No unencrypted key files are written.  Only the two named keystore JSON files
 *       are created under {@code walletDir}.</li>
 * </ul>
 *
 * <p>These wallets are for <strong>Base Sepolia TESTNET ONLY</strong>.
 */
public class WalletGenerator {

    static final String TREASURY_FILENAME = "treasury-wallet.json";
    static final String BENEFICIARY_FILENAME = "beneficiary-wallet.json";

    /**
     * Returns {@code true} if either wallet file already exists in {@code walletDir}.
     * Used to prevent accidental overwrite.
     */
    public boolean walletsExist(Path walletDir) {
        return Files.exists(walletDir.resolve(TREASURY_FILENAME))
            || Files.exists(walletDir.resolve(BENEFICIARY_FILENAME));
    }

    /**
     * Generates two encrypted wallet files.
     *
     * @param treasuryPassword    password for the Treasury keystore (cleared after use)
     * @param beneficiaryPassword password for the Beneficiary keystore (cleared after use)
     * @param walletDir           directory where the two keystore JSON files will be written
     * @return result containing only public addresses and file paths — no private keys
     * @throws Exception if key generation or file write fails
     */
    public WalletGeneratorResult generate(char[] treasuryPassword,
                                           char[] beneficiaryPassword,
                                           Path walletDir) throws Exception {
        Files.createDirectories(walletDir);

        Path treasuryFile = walletDir.resolve(TREASURY_FILENAME);
        Path beneficiaryFile = walletDir.resolve(BENEFICIARY_FILENAME);

        String treasuryAddress = generateAndSave(treasuryPassword, treasuryFile, walletDir);
        // Clear treasury password immediately after the keystore is written
        Arrays.fill(treasuryPassword, '\0');

        String beneficiaryAddress = generateAndSave(beneficiaryPassword, beneficiaryFile, walletDir);
        Arrays.fill(beneficiaryPassword, '\0');

        return new WalletGeneratorResult(
            treasuryAddress, beneficiaryAddress, treasuryFile, beneficiaryFile);
    }

    /**
     * Generates a single key pair, encrypts it as Ethereum Keystore V3 (full scrypt),
     * writes it to {@code outputFile}, and returns the EIP-55 checksum address.
     *
     * <p>Uses {@link WalletUtils#generateWalletFile} which serializes the keystore via
     * web3j's internal Jackson 3.x — no direct jackson-databind dependency required here.
     * The auto-generated timestamp filename is renamed to {@code outputFile}.
     */
    private String generateAndSave(char[] password, Path outputFile, Path walletDir)
            throws CipherException, IOException,
                   InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        ECKeyPair keyPair = Keys.createEcKeyPair();

        // generateWalletFile writes a timestamp-named keystore JSON into walletDir
        // and returns the generated filename.  We rename it to the desired name.
        // Password is converted to String here because the web3j API requires it;
        // the resulting String may linger in the JVM string pool — unavoidable with the web3j 6.1.0 API.
        String generatedName = WalletUtils.generateWalletFile(
            new String(password), keyPair, walletDir.toFile(), true /* full scrypt */);

        Path generatedPath = walletDir.resolve(generatedName);
        Files.move(generatedPath, outputFile, StandardCopyOption.REPLACE_EXISTING);

        // Return only the public address — private key is never returned
        return Keys.toChecksumAddress("0x" + Keys.getAddress(keyPair));
    }
}
