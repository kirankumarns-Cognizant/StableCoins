package com.cognizant.stablecoins.blockchain.integration;

import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import com.cognizant.stablecoins.blockchain.service.RealTestnetBlockchainAdapter;
import com.cognizant.stablecoins.blockchain.service.LocalTestnetSigningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real testnet preflight verification.
 *
 * SAFETY GATE: This test ONLY reads chain state.
 * It does NOT submit any transaction.
 * Real transaction submission requires explicit owner instruction.
 *
 * Runs only with Maven profile: -Ptestnet
 * And env vars: BLOCKCHAIN_MODE=REAL_TESTNET and TESTNET_ENABLED=true
 *
 * Network: Base Sepolia (chain ID 84532)
 * RPC: https://sepolia.base.org
 * Explorer: https://sepolia.basescan.org
 */
class BlockchainRealTestnetIT {

    private static final boolean TESTNET_ACTIVE =
        "REAL_TESTNET".equals(System.getenv("BLOCKCHAIN_MODE")) &&
        "true".equals(System.getenv("TESTNET_ENABLED"));

    private static final long EXPECTED_CHAIN_ID = 84532L;
    private static final String USDC_CONTRACT = "0x036CbD53842c5426634e7929541eC2318f3dCF7e";

    @BeforeAll
    static void checkGuard() {
        assumeTrue(TESTNET_ACTIVE,
            "Skipping — testnet not activated (set BLOCKCHAIN_MODE=REAL_TESTNET and TESTNET_ENABLED=true)");
        assumeTrue(System.getenv("BLOCKCHAIN_RPC_URL") != null
            && !System.getenv("BLOCKCHAIN_RPC_URL").isBlank(),
            "Skipping — BLOCKCHAIN_RPC_URL not set");
    }

    @Test
    void realTestnet_chainIdVerification() {
        // SAFETY: read-only call — no transaction submission
        String rpcUrl = System.getenv("BLOCKCHAIN_RPC_URL");
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));

        BlockchainProperties props = new BlockchainProperties();
        props.setChainId(EXPECTED_CHAIN_ID);
        props.setNetwork("base-sepolia");
        props.setTokenContract(USDC_CONTRACT);
        props.setTokenDecimals(6);
        props.setExplorerBaseUrl("https://sepolia.basescan.org");

        // Dummy signing service — not used for read-only calls
        LocalTestnetSigningService signing = new LocalTestnetSigningService(
            "0x0000000000000000000000000000000000000000000000000000000000000001");

        RealTestnetBlockchainAdapter adapter = new RealTestnetBlockchainAdapter(web3j, signing, props);

        long chainId = adapter.getChainId();
        assertThat(chainId).isEqualTo(EXPECTED_CHAIN_ID);

        web3j.shutdown();
    }

    @Test
    void realTestnet_walletBalanceCheck() {
        // SAFETY: read-only call — no transaction submission
        String rpcUrl = System.getenv("BLOCKCHAIN_RPC_URL");
        String walletAddress = System.getenv("BLOCKCHAIN_WALLET_ADDRESS");
        assumeTrue(walletAddress != null && !walletAddress.isBlank(),
            "Skipping — BLOCKCHAIN_WALLET_ADDRESS not set");

        Web3j web3j = Web3j.build(new HttpService(rpcUrl));

        BlockchainProperties props = new BlockchainProperties();
        props.setChainId(EXPECTED_CHAIN_ID);
        props.setTokenContract(USDC_CONTRACT);
        props.setTokenDecimals(6);

        LocalTestnetSigningService signing = new LocalTestnetSigningService(
            "0x0000000000000000000000000000000000000000000000000000000000000001");

        RealTestnetBlockchainAdapter adapter = new RealTestnetBlockchainAdapter(web3j, signing, props);

        // Check ETH balance (read-only)
        BigDecimal ethBalance = adapter.getNativeBalance(walletAddress);
        assertThat(ethBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // Check USDC balance (read-only)
        BigDecimal usdcBalance = adapter.getTokenBalance(walletAddress, USDC_CONTRACT, 6);
        assertThat(usdcBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        web3j.shutdown();

        // SAFETY GATE: NO transaction submission below this line.
        // Await explicit owner instruction before submitting any transaction.
    }

    @Test
    void realTestnet_currentBlockNumber() {
        // SAFETY: read-only call — no transaction submission
        String rpcUrl = System.getenv("BLOCKCHAIN_RPC_URL");
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));

        BlockchainProperties props = new BlockchainProperties();
        props.setChainId(EXPECTED_CHAIN_ID);

        LocalTestnetSigningService signing = new LocalTestnetSigningService(
            "0x0000000000000000000000000000000000000000000000000000000000000001");

        RealTestnetBlockchainAdapter adapter = new RealTestnetBlockchainAdapter(web3j, signing, props);

        long blockNumber = adapter.getCurrentBlockNumber();
        assertThat(blockNumber).isGreaterThan(0L);

        web3j.shutdown();
    }
}
