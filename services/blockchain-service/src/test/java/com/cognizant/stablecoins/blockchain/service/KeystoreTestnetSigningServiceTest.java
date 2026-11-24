package com.cognizant.stablecoins.blockchain.service;

import com.cognizant.stablecoins.blockchain.config.BlockchainConfig;
import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import com.cognizant.stablecoins.blockchain.config.TestnetProperties;
import com.cognizant.stablecoins.blockchain.enums.BlockchainMode;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;
import static org.assertj.core.api.Assertions.*;

class KeystoreTestnetSigningServiceTest {
    @TempDir static Path directory;
    // Disposable test fixture, unrelated to any owner wallet or password.
    static final String PASSWORD = "  fixture-only-\u00e9-password  ";
    static Path wallet, passwordFile;
    static String address;

    @BeforeAll static void fixture() throws Exception {
        var pair = ECKeyPair.create(BigInteger.valueOf(2));
        address = Credentials.create(pair).getAddress();
        wallet = directory.resolve(WalletUtils.generateWalletFile(PASSWORD, pair, directory.toFile(), false));
        passwordFile = Files.writeString(directory.resolve("password"), PASSWORD);
    }

    @Test void decryptsAndSignsRecoverableTransactionWithoutRpc() throws Exception {
        var signer = new KeystoreTestnetSigningService(wallet, passwordFile, address.toUpperCase().replace("0X", "0x"));
        var raw = RawTransaction.createEtherTransaction(BigInteger.ZERO, BigInteger.ONE,
                BigInteger.valueOf(21000), "0x" + "3".repeat(40), BigInteger.ONE);
        var signed = (SignedRawTransaction) TransactionDecoder.decode(Numeric.toHexString(signer.signTransaction(raw, 84532L)));
        assertThat(signed.getFrom()).isEqualToIgnoringCase(address);
        assertThat(signed.getChainId()).isEqualTo(84532L);
        assertThat(signer.getWalletAddress()).isEqualToIgnoringCase(address);
    }

    @Test void wrongPasswordFailsWithoutCauseOrPasswordInError() throws Exception {
        Path wrong = Files.writeString(directory.resolve("wrong"), "do-not-disclose-this-fixture");
        assertThatThrownBy(() -> new KeystoreTestnetSigningService(wallet, wrong, address))
                .isInstanceOf(IllegalStateException.class).hasMessage("TESTNET_KEYSTORE_UNLOCK_FAILED").hasNoCause();
    }

    @Test void wrongExpectedAddressFails() {
        assertThatThrownBy(() -> new KeystoreTestnetSigningService(wallet, passwordFile, "0x" + "3".repeat(40)))
                .hasMessage("TESTNET_SIGNER_ADDRESS_MISMATCH");
    }

    @Test void missingOrMalformedFilesFailWithoutLibraryCause() throws Exception {
        var malformed = Files.writeString(directory.resolve("malformed.json"), "not-json-sensitive-fixture");
        for (Path file : new Path[]{directory.resolve("absent"), malformed}) {
            assertThatThrownBy(() -> new KeystoreTestnetSigningService(file, passwordFile, address))
                    .hasMessage("TESTNET_KEYSTORE_UNLOCK_FAILED").hasNoCause();
        }
        assertThatThrownBy(() -> new KeystoreTestnetSigningService(wallet, directory.resolve("absent"), address))
                .hasMessage("TESTNET_KEYSTORE_UNLOCK_FAILED").hasNoCause();
    }

    @Test void passwordIsExactAndMustNotContainAnAddedNewline() throws Exception {
        var newline = Files.writeString(directory.resolve("newline"), PASSWORD + "\n");
        var empty = Files.writeString(directory.resolve("empty"), "");
        for (Path file : new Path[]{newline, empty}) {
            assertThatThrownBy(() -> new KeystoreTestnetSigningService(wallet, file, address))
                    .hasMessage("TESTNET_KEYSTORE_UNLOCK_FAILED");
        }
    }

    @Test void expectedAddressRequiredAndMainnetSigningRejected() {
        assertThatThrownBy(() -> new KeystoreTestnetSigningService(wallet, passwordFile, null))
                .hasMessage("TESTNET_EXPECTED_WALLET_ADDRESS_REQUIRED");
        var signer = new KeystoreTestnetSigningService(wallet, passwordFile, address);
        assertThatThrownBy(() -> signer.signTransaction(null, 1L)).hasMessage("PUBLIC_TESTNET_CHAIN_REQUIRED");
    }

    @Test void rejectsKnownMockPrivateKey() throws Exception {
        var file = directory.resolve(WalletUtils.generateWalletFile(PASSWORD,
                ECKeyPair.create(BigInteger.ONE), directory.toFile(), false));
        assertThatThrownBy(() -> new KeystoreTestnetSigningService(file, passwordFile,
                Credentials.create("1").getAddress())).hasMessage("TESTNET_KNOWN_MOCK_SIGNER_REJECTED");
    }

    MockEnvironment environment() {
        var env = new MockEnvironment().withProperty("BLOCKCHAIN_KEYSTORE_FILE", wallet.toString())
                .withProperty("BLOCKCHAIN_KEYSTORE_PASSWORD_FILE", passwordFile.toString())
                .withProperty("BLOCKCHAIN_EXPECTED_WALLET_ADDRESS", address);
        env.setActiveProfiles("local", "testnet");
        return env;
    }

    @Test void factoryUsesKeystoreOnlyWhenBothFlagsEnabled() {
        var config = new BlockchainConfig();
        var props = new BlockchainProperties();
        var testnet = new TestnetProperties();
        props.setMode(BlockchainMode.REAL_TESTNET);
        // Disabled flag ignores even unusable file settings and stays MOCK per ADR-010.
        assertThat(config.walletSigningService(props, testnet, new MockEnvironment()).getWalletAddress())
                .isEqualToIgnoringCase(Credentials.create("1").getAddress());
        testnet.setEnabled(true);
        assertThat(config.walletSigningService(props, testnet, environment()))
                .isInstanceOf(KeystoreTestnetSigningService.class);
        props.setMode(BlockchainMode.MOCK);
        assertThat(config.walletSigningService(props, testnet, new MockEnvironment()).getWalletAddress())
                .isEqualToIgnoringCase(Credentials.create("1").getAddress());
    }

    @Test void factoryRejectsMixedOrIncompleteSourcesAndAwsProfile() {
        var config = new BlockchainConfig();
        var props = new BlockchainProperties(); props.setMode(BlockchainMode.REAL_TESTNET);
        var testnet = new TestnetProperties(); testnet.setEnabled(true);
        var mixed = environment().withProperty("BLOCKCHAIN_WALLET_PRIVATE_KEY", "fixture-never-a-key");
        assertThatThrownBy(() -> config.walletSigningService(props, testnet, mixed))
                .hasMessage("TESTNET_SIGNING_CONFIGURATION_CONFLICT");
        var incomplete = environment().withProperty("BLOCKCHAIN_KEYSTORE_PASSWORD_FILE", "");
        assertThatThrownBy(() -> config.walletSigningService(props, testnet, incomplete))
                .hasMessage("TESTNET_SIGNING_CONFIGURATION_CONFLICT");
        var aws = environment(); aws.setActiveProfiles("aws-dev", "local", "testnet");
        assertThatThrownBy(() -> config.walletSigningService(props, testnet, aws))
                .hasMessage("FILE_KEYSTORE_SIGNER_LOCAL_ONLY");
        props.setChainId(1L);
        assertThatThrownBy(() -> config.walletSigningService(props, testnet, environment()))
                .hasMessage("PUBLIC_TESTNET_CHAIN_REQUIRED");
    }
}
