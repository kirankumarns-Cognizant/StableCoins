package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.blockchain.service.WalletSigningService;
import com.cognizant.stablecoins.flow.SettlementNetwork.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;
import org.web3j.crypto.RawTransaction;

/** Opt-in local PRIVATE_DEMO capability, independent from REAL_TESTNET and its public-chain guards. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.besu.enabled", havingValue = "true")
public class BesuConfiguration {
    @Bean(destroyMethod = "close")
    public BesuRuntime besuRuntime(Environment env, WalletSigningService publicSigner) {
        if (!env.acceptsProfiles(Profiles.of("local")) || env.acceptsProfiles(Profiles.of("aws-dev")))
            throw new IllegalStateException("BESU_PRIVATE_DEMO_LOCAL_ONLY");
        var chain = new Chain("besu-local", BesuSettings.CHAIN_ID, required(env, "BESU_GENESIS_HASH"),
                Classification.PRIVATE_DEMO);
        var context = new Context(new Token(chain, required(env, "BESU_TOKEN_CONTRACT"), "DEMOUSD", 6,
                required(env, "BESU_TOKEN_CODE_HASH")), new NativeAsset(chain, "BESU_GAS", 18),
                required(env, "BESU_TREASURY_ADDRESS"), FeeModel.EXECUTION_ONLY, "besu-demo-v1");
        if (context.treasury().equalsIgnoreCase(publicSigner.getWalletAddress()))
            throw new IllegalArgumentException("BESU_PUBLIC_TREASURY_REUSE_REJECTED");
        var settings = new BesuSettings(context, addresses(env, "BESU_VALIDATOR_ADDRESSES"),
                addresses(env, "BESU_RECIPIENT_ADDRESSES"), BigInteger.valueOf(1000), Duration.ofSeconds(30));
        var rpc = new BesuHttpRpc(required(env, "BESU_RPC_URL"));
        Clock clock = Clock.systemUTC();
        try {
            // Validate chain before touching encrypted signing material.
            var addressOnly = new WalletSigningService() {
                @Override public String getWalletAddress() { return context.treasury(); }
                @Override public byte[] signTransaction(RawTransaction tx, long id) {
                    throw new IllegalStateException("BESU_SIGNER_NOT_LOADED");
                }
            };
            new BesuBlockchainAdapter(settings, rpc, addressOnly, clock).verifyReadiness();
            var signer = new BesuKeystoreSigningService(Path.of(required(env, "BESU_KEYSTORE_FILE")),
                    Path.of(required(env, "BESU_PASSWORD_FILE")), context.treasury(), publicSigner.getWalletAddress());
            var adapter = new BesuBlockchainAdapter(settings, rpc, signer, clock);
            adapter.verifyReadiness(); // KDF may have taken time; do not cache the initial observation.
            return new BesuRuntime(adapter, rpc, clock);
        } catch (RuntimeException failure) {
            rpc.close();
            throw failure;
        }
    }
    private static String required(Environment env, String name) {
        String value = env.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("BESU_CONFIGURATION_MISSING_" + name);
        return value;
    }
    private static Set<String> addresses(Environment env, String name) {
        return Arrays.stream(required(env, name).split(",", -1)).map(String::strip)
                .collect(Collectors.toUnmodifiableSet());
    }
}
