package com.cognizant.stablecoins.flow;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable identity for additive multi-network settlement (ADR-016).
 * No RPC URL, signing secret or client-selected account mapping belongs here.
 * A chain ID alone does not identify a private chain after a reset.
 */
public final class SettlementNetwork {
    private SettlementNetwork() {}

    public enum Classification { PUBLIC_TESTNET, PRIVATE_DEMO }
    public enum FeeModel { BASE_L1_AND_EXECUTION, EXECUTION_ONLY }

    public record Chain(String networkId, long chainId, String genesisHash,
                        Classification classification) {
        public Chain {
            if (networkId == null || !networkId.matches("[a-z][a-z0-9-]{1,49}") || chainId <= 0)
                throw new IllegalArgumentException("INVALID_NETWORK_IDENTITY");
            genesisHash = hash(genesisHash);
            Objects.requireNonNull(classification, "NETWORK_CLASSIFICATION_REQUIRED");
            if (classification == Classification.PRIVATE_DEMO &&
                    (chainId == 84532 || chainId == 11155111 || chainId == 1))
                throw new IllegalArgumentException("PRIVATE_NETWORK_USES_PUBLIC_CHAIN_ID");
        }
        public String key() { return networkId + ":" + chainId + ":" + genesisHash; }
    }

    public record Token(Chain chain, String contract, String symbol, int decimals, String codeHash) {
        public Token {
            Objects.requireNonNull(chain, "CHAIN_REQUIRED");
            contract = address(contract);
            if (symbol == null || !symbol.matches("[A-Z][A-Z0-9_]{0,19}") ||
                    decimals < 0 || decimals > 18)
                throw new IllegalArgumentException("INVALID_TOKEN_IDENTITY");
            codeHash = hash(codeHash);
            if (chain.classification() == Classification.PRIVATE_DEMO && "USDC".equals(symbol))
                throw new IllegalArgumentException("PRIVATE_DEMO_IS_NOT_CIRCLE_USDC");
        }
        public String key() { return chain.key() + ":" + contract; }
    }

    public record NativeAsset(Chain chain, String symbol, int decimals) {
        public NativeAsset {
            Objects.requireNonNull(chain, "CHAIN_REQUIRED");
            if (symbol == null || !symbol.matches("[A-Z][A-Z0-9_]{0,19}") || decimals != 18)
                throw new IllegalArgumentException("INVALID_NATIVE_ASSET");
            if (chain.classification() == Classification.PRIVATE_DEMO && !"BESU_GAS".equals(symbol))
                throw new IllegalArgumentException("PRIVATE_GAS_ASSET_MUST_BE_BESU_GAS");
        }
    }

    /** Value persisted with a review/execution; equality includes genesis and bytecode. */
    public record Context(Token token, NativeAsset nativeAsset, String treasury,
                          FeeModel feeModel, String policyVersion) {
        public Context {
            Objects.requireNonNull(token, "TOKEN_REQUIRED");
            Objects.requireNonNull(nativeAsset, "NATIVE_ASSET_REQUIRED");
            treasury = address(treasury);
            Objects.requireNonNull(feeModel, "FEE_MODEL_REQUIRED");
            FlowContract.identifier(policyVersion, 64);
            if (!token.chain().equals(nativeAsset.chain()))
                throw new IllegalArgumentException("TOKEN_AND_GAS_CHAIN_MISMATCH");
            if (token.chain().classification() == Classification.PRIVATE_DEMO &&
                    feeModel != FeeModel.EXECUTION_ONLY)
                throw new IllegalArgumentException("PRIVATE_FEE_MODEL_MISMATCH");
            if (token.chain().chainId() == 84532 && feeModel != FeeModel.BASE_L1_AND_EXECUTION)
                throw new IllegalArgumentException("BASE_L1_FEE_EVIDENCE_REQUIRED");
        }
        public String nonceScope() { return token.chain().key() + ":" + treasury; }
        public String liquidityScope() { return nonceScope() + ":" + token.contract(); }
    }

    public static String address(String value) {
        if (value == null || !value.matches("0x[0-9a-fA-F]{40}") ||
                value.equalsIgnoreCase("0x" + "0".repeat(40)))
            throw new IllegalArgumentException("INVALID_NETWORK_ADDRESS");
        return value.toLowerCase(Locale.ROOT);
    }

    public static String hash(String value) {
        if (value == null || !value.matches("0x[0-9a-fA-F]{64}") ||
                value.equalsIgnoreCase("0x" + "0".repeat(64)))
            throw new IllegalArgumentException("INVALID_NETWORK_HASH");
        return value.toLowerCase(Locale.ROOT);
    }
}
