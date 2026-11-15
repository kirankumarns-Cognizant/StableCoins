package com.cognizant.stablecoins.blockchain.config;

import com.cognizant.stablecoins.blockchain.enums.BlockchainMode;
import com.cognizant.stablecoins.blockchain.service.BlockchainAdapter;
import com.cognizant.stablecoins.blockchain.service.MockBlockchainAdapter;
import com.cognizant.stablecoins.blockchain.service.RealTestnetBlockchainAdapter;
import com.cognizant.stablecoins.blockchain.service.WalletSigningService;
import com.cognizant.stablecoins.blockchain.service.LocalTestnetSigningService;
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
    public WalletSigningService walletSigningService(BlockchainProperties props, TestnetProperties testnetProps) {
        boolean isReal = props.getMode() == BlockchainMode.REAL_TESTNET && testnetProps.isEnabled();
        if (isReal) {
            String privateKey = System.getenv("BLOCKCHAIN_WALLET_PRIVATE_KEY");
            if (privateKey == null || privateKey.isBlank()) {
                throw new IllegalStateException(
                    "BLOCKCHAIN_WALLET_PRIVATE_KEY env var is required for REAL_TESTNET mode");
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
