package com.cognizant.stablecoins.blockchain.service;

import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * REAL_TESTNET implementation of BlockchainAdapter.
 * Makes actual RPC calls to Base Sepolia testnet.
 * Chain ID: 84532
 * RPC: https://sepolia.base.org
 * Token contract: 0x036CbD53842c5426634e7929541eC2318f3dCF7e (USDC on Base Sepolia)
 */
public class RealTestnetBlockchainAdapter implements BlockchainAdapter {

    private static final Logger log = LoggerFactory.getLogger(RealTestnetBlockchainAdapter.class);

    private final Web3j web3j;
    private final WalletSigningService signingService;
    private final BlockchainProperties props;
    private final org.web3j.protocol.http.HttpService receiptRpc;

    public RealTestnetBlockchainAdapter(Web3j web3j, WalletSigningService signingService,
                                         BlockchainProperties props) {
        this.web3j = web3j;
        this.signingService = signingService;
        this.props = props;
        var client=new okhttp3.OkHttpClient.Builder()
            .connectTimeout(props.getRpcConnectTimeoutMs(),java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(props.getRpcReadTimeoutMs(),java.util.concurrent.TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false).build();
        this.receiptRpc=new org.web3j.protocol.http.HttpService(props.getRpcUrl(),client);
    }

    @Override public String getEvidenceMode() { return "LIVE"; }
    @Override public BigDecimal getGasPriceWei() {
        try {
            var result=web3j.ethGasPrice().send();
            if(result.hasError()) throw new BlockchainRpcException("Gas price RPC rejected");
            return new BigDecimal(result.getGasPrice());
        } catch(Exception e) { throw new BlockchainRpcException("Failed to get gas price", e); }
    }
    @Override public long getPendingNonce(String address) {
        try {
            var result=web3j.ethGetTransactionCount(address,DefaultBlockParameterName.PENDING).send();
            if(result.hasError()) throw new BlockchainRpcException("Nonce RPC rejected");
            return result.getTransactionCount().longValueExact();
        } catch(Exception e) { throw new BlockchainRpcException("Failed to get pending nonce", e); }
    }

    @Override
    public long getChainId() {
        try {
            return web3j.ethChainId().send().getChainId().longValue();
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to get chain ID", e);
        }
    }

    @Override
    public BigDecimal getNativeBalance(String address) {
        try {
            EthGetBalance result = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            if (result.hasError()) {
                throw new BlockchainRpcException("ethGetBalance error: " + result.getError().getMessage());
            }
            // Convert from wei to ETH
            return Convert.fromWei(result.getBalance().toString(), Convert.Unit.ETHER);
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to get native balance", e);
        }
    }

    @Override
    public BigDecimal getTokenBalance(String address, String tokenContract, int decimals) {
        try {
            // Call ERC-20 balanceOf(address)
            Function balanceOf = new Function(
                "balanceOf",
                Collections.singletonList(new Address(address)),
                Collections.singletonList(new TypeReference<Uint256>() {})
            );
            String encodedFunction = FunctionEncoder.encode(balanceOf);
            EthCall call = web3j.ethCall(
                Transaction.createEthCallTransaction(address, tokenContract, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();
            if (call.hasError()) {
                throw new BlockchainRpcException("balanceOf error: " + call.getError().getMessage());
            }
            String rawValue = call.getValue();
            BigInteger rawBig = new BigInteger(Numeric.cleanHexPrefix(rawValue), 16);
            return new BigDecimal(rawBig).movePointLeft(decimals);
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to get token balance", e);
        }
    }

    @Override
    public BigDecimal estimateTransferGas(String from, String to, String tokenContract,
                                           BigDecimal amount, int decimals) {
        try {
            BigInteger amountInSmallest = amount.movePointRight(decimals).toBigIntegerExact();
            Function transferFn = buildTransferFunction(to, amountInSmallest);
            String data = FunctionEncoder.encode(transferFn);
            EthEstimateGas result = web3j.ethEstimateGas(
                Transaction.createFunctionCallTransaction(from, null, null, null, tokenContract, data)
            ).send();
            if (result.hasError()) {
                throw new BlockchainRpcException("estimateGas error: " + result.getError().getMessage());
            }
            return new BigDecimal(result.getAmountUsed());
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to estimate gas", e);
        }
    }

    @Override
    public byte[] signRawTransaction(long nonce, String to, String tokenContract, BigDecimal amount,
                                      int decimals, BigDecimal gasLimit, BigDecimal gasPrice) {
        try {
            BigInteger amountInSmallest = amount.movePointRight(decimals).toBigIntegerExact();
            Function transferFn = buildTransferFunction(to, amountInSmallest);
            String data = FunctionEncoder.encode(transferFn);
            RawTransaction rawTx = RawTransaction.createTransaction(
                BigInteger.valueOf(nonce),
                gasPrice.toBigIntegerExact(),
                gasLimit.toBigIntegerExact(),
                tokenContract,
                BigInteger.ZERO,
                data
            );
            return signingService.signTransaction(rawTx, props.getChainId());
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to sign transaction", e);
        }
    }

    @Override
    public String submitRawTransaction(byte[] signedTx) {
        try {
            String hexTx = Numeric.toHexString(signedTx);
            EthSendTransaction result = web3j.ethSendRawTransaction(hexTx).send();
            if (result.hasError()) {
                throw new BlockchainRpcException(
                    "ethSendRawTransaction error: " + result.getError().getMessage());
            }
            return result.getTransactionHash();
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to submit transaction", e);
        }
    }

    @Override
    public Optional<BlockchainReceiptResult> getReceipt(String txHash) {
        try {
            // Preserve L2 extension fields and reuse the configured RPC connection pool.
            RawReceiptResponse response=new org.web3j.protocol.core.Request<>("eth_getTransactionReceipt",java.util.List.of(txHash),receiptRpc,RawReceiptResponse.class).send();
            if(response.hasError())throw new BlockchainRpcException("RECEIPT_RPC_REJECTED");
            var receipt=response.getResult();
            return receipt==null||receipt.isNull()?Optional.empty():Optional.of(ReceiptFeeParser.parse(new com.fasterxml.jackson.databind.ObjectMapper().readTree(receipt.toString()),props.getChainId()));
        } catch (BlockchainRpcException e) {
            throw e;
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to get receipt", e);
        }
    }

    @Override
    public long getCurrentBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber().longValue();
        } catch (Exception e) {
            throw new BlockchainRpcException("Failed to get block number", e);
        }
    }

    @Override
    public String getFromAddress() {
        return signingService.getWalletAddress();
    }

    @jakarta.annotation.PreDestroy
    public void closeReceiptRpc() throws java.io.IOException { receiptRpc.close(); }

    public static class RawReceiptResponse extends org.web3j.protocol.core.Response<tools.jackson.databind.JsonNode> {}

    private Function buildTransferFunction(String to, BigInteger amount) {
        return new Function(
            "transfer",
            Arrays.asList(new Address(to), new Uint256(amount)),
            Collections.singletonList(new TypeReference<Bool>() {})
        );
    }
}
