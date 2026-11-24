package com.cognizant.stablecoins.blockchain.config;

import com.cognizant.stablecoins.blockchain.enums.BlockchainMode;
import com.cognizant.stablecoins.blockchain.service.BlockchainAdapter;
import com.cognizant.stablecoins.blockchain.service.MockBlockchainAdapter;
import com.cognizant.stablecoins.blockchain.service.RealTestnetBlockchainAdapter;
import com.cognizant.stablecoins.blockchain.service.WalletSigningService;
import com.cognizant.stablecoins.blockchain.service.LocalTestnetSigningService;
import com.cognizant.stablecoins.blockchain.service.KeystoreTestnetSigningService;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class BlockchainConfig {

    private static final Logger log = LoggerFactory.getLogger(BlockchainConfig.class);

    @Bean
    public Web3j web3j(BlockchainProperties props, TestnetProperties testnetProps) {
        boolean isReal = props.getMode() == BlockchainMode.REAL_TESTNET && testnetProps.isEnabled();
        if (isReal && (props.getRpcUrl() == null || props.getRpcUrl().isBlank())) {
            throw new IllegalStateException("BLOCKCHAIN_RPC_URL is required for REAL_TESTNET mode");
        }
        var client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(props.getRpcConnectTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(props.getRpcReadTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                // Transaction submission must never be blindly retried by the HTTP transport.
                .retryOnConnectionFailure(false)
                .build();
        log.info("service=blockchain-service event=web3j_configured realTestnet={}", isReal);
        return Web3j.build(new HttpService(isReal ? props.getRpcUrl() : props.getMockRpcUrl(), client));
    }

    @Bean
    public WalletSigningService walletSigningService(BlockchainProperties props, TestnetProperties testnetProps,
                                                     Environment environment) {
        boolean isReal = props.getMode() == BlockchainMode.REAL_TESTNET && testnetProps.isEnabled();
        if (isReal) {
            String privateKey = environment.getProperty("BLOCKCHAIN_WALLET_PRIVATE_KEY");
            String keystore = environment.getProperty("BLOCKCHAIN_KEYSTORE_FILE");
            String passwordFile = environment.getProperty("BLOCKCHAIN_KEYSTORE_PASSWORD_FILE");
            boolean hasKeystore = keystore != null && !keystore.isBlank();
            boolean hasPasswordFile = passwordFile != null && !passwordFile.isBlank();
            boolean hasPrivateKey = privateKey != null && !privateKey.isBlank();
            if (hasKeystore || hasPasswordFile) {
                if (hasPrivateKey || !hasKeystore || !hasPasswordFile) {
                    throw new IllegalStateException("TESTNET_SIGNING_CONFIGURATION_CONFLICT");
                }
                if (props.getChainId() != 84532L && props.getChainId() != 11155111L) {
                    throw new IllegalStateException("PUBLIC_TESTNET_CHAIN_REQUIRED");
                }
                if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))
                        || environment.acceptsProfiles(org.springframework.core.env.Profiles.of("aws-dev"))) {
                    throw new IllegalStateException("FILE_KEYSTORE_SIGNER_LOCAL_ONLY");
                }
                var signer = new KeystoreTestnetSigningService(java.nio.file.Path.of(keystore),
                        java.nio.file.Path.of(passwordFile),
                        environment.getProperty("BLOCKCHAIN_EXPECTED_WALLET_ADDRESS"));
                log.info("service=blockchain-service event=keystore_signer_verified wallet={}", signer.getWalletAddress());
                return signer;
            }
            if (privateKey == null || privateKey.isBlank()) {
                throw new IllegalStateException(
                    "TESTNET_SIGNING_CONFIGURATION_REQUIRED");
            }
            return new LocalTestnetSigningService(privateKey);
        }
        // MOCK mode — use a known-safe dummy key (all-ones private key, non-zero, deterministic)
        return new LocalTestnetSigningService(
            "0x0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Bean
    public BlockchainAdapter blockchainAdapter(BlockchainProperties props,
                                                TestnetProperties testnetProps,
                                                Web3j web3j,
                                                WalletSigningService signingService) {
        boolean isReal = props.getMode() == BlockchainMode.REAL_TESTNET && testnetProps.isEnabled();
        if (isReal) {
            log.info("service=blockchain-service event=mode_real_testnet network={} chainId={}",
                props.getNetwork(), props.getChainId());
            return new RealTestnetBlockchainAdapter(web3j, signingService, props);
        }
        log.info("service=blockchain-service event=mode_mock");
        return new MockBlockchainAdapter(props);
    }
}
