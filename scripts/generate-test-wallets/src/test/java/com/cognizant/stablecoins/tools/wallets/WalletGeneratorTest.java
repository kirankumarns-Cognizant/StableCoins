package com.cognizant.stablecoins.tools.wallets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.exception.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.WalletUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the StableCoinsDemo test wallet generator.
 *
 * <p>Tests verify security properties and functional correctness without
 * exposing or logging any private key material.  Test passwords are fixture
 * values used only within this test class and have no security significance.
 */
class WalletGeneratorTest {

    private static final String TEST_PASSWORD = "TestWalletPass!99";
    private static final String WRONG_PASSWORD = "definitively-wrong-password";

    // -------------------------------------------------------------------------
    // 1. Two distinct wallets are generated
    // -------------------------------------------------------------------------

    @Test
    void generate_producesTwoWallets(@TempDir Path tempDir) throws Exception {
        WalletGeneratorResult result = generate(tempDir);

        assertThat(result.getTreasuryAddress()).isNotNull().isNotEmpty();
        assertThat(result.getBeneficiaryAddress()).isNotNull().isNotEmpty();
        assertThat(result.getTreasuryWalletFile()).isNotNull();
        assertThat(result.getBeneficiaryWalletFile()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // 2. Both public addresses are valid EVM addresses
    // -------------------------------------------------------------------------

    @Test
    void generatedAddresses_areValidEvmFormat(@TempDir Path tempDir) throws Exception {
        WalletGeneratorResult result = generate(tempDir);

        // EVM address: 0x followed by exactly 40 hex characters
        assertThat(result.getTreasuryAddress()).matches("0x[0-9a-fA-F]{40}");
        assertThat(result.getBeneficiaryAddress()).matches("0x[0-9a-fA-F]{40}");
    }

    // -------------------------------------------------------------------------
    // 3. Treasury != Beneficiary
    // -------------------------------------------------------------------------

    @Test
    void treasuryAndBeneficiary_haveDistinctAddresses(@TempDir Path tempDir) throws Exception {
        WalletGeneratorResult result = generate(tempDir);

        assertThat(result.getTreasuryAddress())
            .isNotEqualToIgnoringCase(result.getBeneficiaryAddress());
    }

    // -------------------------------------------------------------------------
    // 4. Encrypted wallet files are created at expected paths
    // -------------------------------------------------------------------------

    @Test
    void encryptedWalletFiles_areCreatedAtExpectedPaths(@TempDir Path tempDir) throws Exception {
        WalletGeneratorResult result = generate(tempDir);

        assertThat(result.getTreasuryWalletFile()).exists();
        assertThat(result.getBeneficiaryWalletFile()).exists();
        assertThat(result.getTreasuryWalletFile().getFileName().toString())
            .isEqualTo(WalletGenerator.TREASURY_FILENAME);
        assertThat(result.getBeneficiaryWalletFile().getFileName().toString())
            .isEqualTo(WalletGenerator.BENEFICIARY_FILENAME);
    }

    // -------------------------------------------------------------------------
    // 5. No unencrypted private key files are written alongside the keystores
    // -------------------------------------------------------------------------

    @Test
    void noUnencryptedKeyFiles_areCreated(@TempDir Path tempDir) throws Exception {
        generate(tempDir);

        // Exactly the two keystore files — no raw key dumps, no .pem, no .key
        File[] files = tempDir.toFile().listFiles();
        assertThat(files).isNotNull().hasSize(2);
        assertThat(files)
            .extracting(File::getName)
            .containsExactlyInAnyOrder(
                WalletGenerator.TREASURY_FILENAME,
                WalletGenerator.BENEFICIARY_FILENAME);
    }

    // -------------------------------------------------------------------------
    // 6. Wallet file CANNOT be decrypted with an incorrect password
    // -------------------------------------------------------------------------

    @Test
    void walletFile_cannotBeDecryptedWithWrongPassword(@TempDir Path tempDir) throws Exception {
        WalletGeneratorResult result = generate(tempDir);

        assertThatThrownBy(() ->
            WalletUtils.loadCredentials(WRONG_PASSWORD, result.getTreasuryWalletFile().toFile()))
            .isInstanceOf(CipherException.class);
    }

    // -------------------------------------------------------------------------
    // 7. Wallet file CAN be decrypted with the correct password
    //    (proves encryption round-trip is valid)
    //    Note: loaded Credentials contains private key but it is never logged or asserted on
    // -------------------------------------------------------------------------

    @Test
    void walletFile_canBeDecryptedWithCorrectPassword(@TempDir Path tempDir) throws Exception {
        WalletGeneratorResult result = generate(tempDir);

        // Decrypt and verify the address matches — private key field is intentionally ignored
        Credentials loaded = WalletUtils.loadCredentials(
            TEST_PASSWORD, result.getTreasuryWalletFile().toFile());

        assertThat(loaded.getAddress())
            .isEqualToIgnoringCase(result.getTreasuryAddress());
    }

    // -------------------------------------------------------------------------
    // 8. Console output does not contain the private key or the password
    // -------------------------------------------------------------------------

    @Test
    void consoleOutput_doesNotContainPasswordOrPrivateKeyMarkers(@TempDir Path tempDir) {
        ByteArrayOutputStream bosOut = new ByteArrayOutputStream();
        ByteArrayOutputStream bosErr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bosOut);
        PrintStream err = new PrintStream(bosErr);

        // PasswordReader returns the same test password on every call (4 calls: 2 × password + confirm)
        WalletGeneratorMain.PasswordReader reader = prompt -> TEST_PASSWORD.toCharArray();

        int exitCode = WalletGeneratorMain.run(reader, out, err, tempDir);
        assertThat(exitCode).isEqualTo(0);

        String output = bosOut.toString();

        // Password must not appear in output
        assertThat(output).doesNotContain(TEST_PASSWORD);

        // A raw EVM private key would be 0x + 64 hex chars (66 chars total).
        // Our output contains only 0x + 40 hex char addresses — this asserts no private key leaked.
        // doesNotMatch uses full-string regex; (?s) enables dot-matches-newline
        assertThat(output).doesNotMatch("(?s).*0x[0-9a-fA-F]{64}.*");

        // Required disclosure markers must be present
        assertThat(output).contains("PRIVATE KEYS: NOT DISPLAYED");
        assertThat(output).contains("PASSWORDS: NOT DISPLAYED");
    }

    // -------------------------------------------------------------------------
    // 9. Existing files cause generation to abort — no overwrite
    // -------------------------------------------------------------------------

    @Test
    void existingTreasuryFile_abortsGenerationWithCode2(@TempDir Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve(WalletGenerator.TREASURY_FILENAME));

        ByteArrayOutputStream bosErr = new ByteArrayOutputStream();
        WalletGeneratorMain.PasswordReader reader = prompt -> TEST_PASSWORD.toCharArray();

        int exitCode = WalletGeneratorMain.run(
            reader, new PrintStream(new ByteArrayOutputStream()), new PrintStream(bosErr), tempDir);

        assertThat(exitCode).isEqualTo(2);
        assertThat(bosErr.toString()).contains("TEST WALLETS ALREADY EXIST");
    }

    @Test
    void existingBeneficiaryFile_abortsGenerationWithCode2(@TempDir Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve(WalletGenerator.BENEFICIARY_FILENAME));

        int exitCode = WalletGeneratorMain.run(
            prompt -> TEST_PASSWORD.toCharArray(),
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream()),
            tempDir);

        assertThat(exitCode).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // 10. Addresses are EIP-55 checksum-encoded
    // -------------------------------------------------------------------------

    @Test
    void addresses_areEip55ChecksumEncoded(@TempDir Path tempDir) throws Exception {
        WalletGeneratorResult result = generate(tempDir);

        // Applying toChecksumAddress to an already-checksummed address returns the same value
        assertThat(result.getTreasuryAddress())
            .isEqualTo(Keys.toChecksumAddress(result.getTreasuryAddress()));
        assertThat(result.getBeneficiaryAddress())
            .isEqualTo(Keys.toChecksumAddress(result.getBeneficiaryAddress()));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private WalletGeneratorResult generate(Path walletDir) throws Exception {
        WalletGenerator gen = new WalletGenerator();
        return gen.generate(TEST_PASSWORD.toCharArray(), TEST_PASSWORD.toCharArray(), walletDir);
    }
}
