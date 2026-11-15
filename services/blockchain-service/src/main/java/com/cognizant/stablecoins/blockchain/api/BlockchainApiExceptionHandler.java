package com.cognizant.stablecoins.blockchain.api;
import com.cognizant.stablecoins.blockchain.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
/** Stable error codes only; never expose raw RPC/database exceptions or credential-bearing URLs. */
@RestControllerAdvice(basePackageClasses=BlockchainTransactionController.class)
public class BlockchainApiExceptionHandler {
    public record Error(String code) {}
    @ExceptionHandler({InsufficientGasException.class,InsufficientTokenBalanceException.class})
    ResponseEntity<Error> insufficient(RuntimeException e) { return ResponseEntity.status(409).body(new Error("INSUFFICIENT_TREASURY_LIQUIDITY")); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Error> conflict(IllegalStateException e) { return ResponseEntity.status(409).body(new Error("EXECUTION_STATE_CONFLICT")); }
    @ExceptionHandler(BlockchainConfigurationException.class)
    ResponseEntity<Error> configuration(BlockchainConfigurationException e) {
        boolean input="INVALID_RECIPIENT_ADDRESS".equals(e.getMessage()) || "INVALID_USDC_AMOUNT".equals(e.getMessage());
        return ResponseEntity.status(input ? 400 : 503).body(new Error(input ? "INVALID_TRANSFER_REQUEST" : "EXECUTION_CONFIGURATION_UNAVAILABLE"));
    }
    @ExceptionHandler(BlockchainRpcException.class)
    ResponseEntity<Error> rpc(BlockchainRpcException e) { return ResponseEntity.status(503).body(new Error("BLOCKCHAIN_RPC_UNAVAILABLE")); }
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Error> invalid(Exception e) { return ResponseEntity.badRequest().body(new Error("INVALID_TRANSFER_REQUEST")); }
}
