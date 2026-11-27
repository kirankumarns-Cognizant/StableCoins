package com.cognizant.stablecoins.blockchain.besu;

import java.math.BigInteger;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;
import static org.assertj.core.api.Assertions.*;

class BesuKeystoreSigningServiceTest {
    @TempDir static Path directory;
    static Path wallet, password;
    static final String PASSWORD = "  private-fixture-only-\u00e9  ";
    static final Credentials CREDENTIALS = Credentials.create("2");
    @BeforeAll static void createDisposableFixture() throws Exception {
        wallet = directory.resolve(WalletUtils.generateWalletFile(PASSWORD, CREDENTIALS.getEcKeyPair(), directory.toFile(), false));
        password = Files.writeString(directory.resolve("password"), PASSWORD);
        secure(wallet); secure(password);
    }
    static void secure(Path file) throws Exception {
        if (Files.getFileStore(file).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }
    @Test void exactPasswordUnlocksAndSignatureIsPrivateChainBound() throws Exception {
        var signer = new BesuKeystoreSigningService(wallet, password, CREDENTIALS.getAddress(), null);
        var raw = RawTransaction.createTransaction(BigInteger.ZERO, BigInteger.valueOf(1000),
                BigInteger.valueOf(75000), BesuBlockchainAdapterTest.CONTRACT, BigInteger.ZERO, BesuBlockchainAdapterTest.DATA);
        var decoded = (SignedRawTransaction) TransactionDecoder.decode(Numeric.toHexString(signer.signTransaction(raw, BesuSettings.CHAIN_ID)));
        assertThat(decoded.getChainId()).isEqualTo(BesuSettings.CHAIN_ID);
        assertThat(decoded.getFrom()).isEqualToIgnoringCase(CREDENTIALS.getAddress());
        assertThatThrownBy(() -> signer.signTransaction(raw, 84532)).hasMessage("BESU_PRIVATE_CHAIN_REQUIRED");
        assertThatThrownBy(() -> signer.signTransaction(raw, 1)).hasMessage("BESU_PRIVATE_CHAIN_REQUIRED");
    }
    @Test void wrongOrNewlineAlteredPasswordFailsWithoutLeakingInput() throws Exception {
        for (String value : new String[]{"wrong-fixture-secret", PASSWORD + "\n"}) {
            Path file = Files.writeString(directory.resolve("wrong"), value); secure(file);
            assertThatThrownBy(() -> new BesuKeystoreSigningService(wallet, file, CREDENTIALS.getAddress(), null))
                    .hasMessage("BESU_KEYSTORE_UNLOCK_FAILED").hasNoCause();
        }
    }
    @Test void rejectsAddressMismatchAndPublicTreasuryReuse() {
        assertThatThrownBy(() -> new BesuKeystoreSigningService(wallet, password, BesuBlockchainAdapterTest.RECIPIENT, null))
                .hasMessage("BESU_SIGNER_ADDRESS_MISMATCH");
        assertThatThrownBy(() -> new BesuKeystoreSigningService(wallet, password, CREDENTIALS.getAddress(), CREDENTIALS.getAddress()))
                .hasMessage("BESU_PUBLIC_TREASURY_REUSE_REJECTED");
    }
    @Test void missingOrOverexposedFilesFailClosed() throws Exception {
        assertThatThrownBy(() -> new BesuKeystoreSigningService(directory.resolve("missing"), password, CREDENTIALS.getAddress(), null))
                .hasMessage("BESU_KEYSTORE_UNLOCK_FAILED").hasNoCause();
        if (Files.getFileStore(password).supportsFileAttributeView("posix")) {
            Path broad = Files.writeString(directory.resolve("broad"), PASSWORD);
            Files.setPosixFilePermissions(broad, PosixFilePermissions.fromString("rw-r--r--"));
            assertThatThrownBy(() -> new BesuKeystoreSigningService(wallet, broad, CREDENTIALS.getAddress(), null))
                    .hasMessage("BESU_KEYSTORE_UNLOCK_FAILED");
        }
    }
}
