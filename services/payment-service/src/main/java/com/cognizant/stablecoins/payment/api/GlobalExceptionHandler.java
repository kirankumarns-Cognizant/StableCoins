package com.cognizant.stablecoins.payment.api;

import com.cognizant.stablecoins.payment.api.dto.ApiError;
import com.cognizant.stablecoins.payment.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        ApiError error = new ApiError("INVALID_REQUEST", "Request validation failed");
        error.setDetails(details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        ApiError error = new ApiError("INVALID_REQUEST",
                "Required header missing: " + ex.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        ApiError error = new ApiError("INVALID_REQUEST",
                "Malformed or unreadable request body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler({AccountNotFoundException.class, BeneficiaryNotFoundException.class,
                        PaymentNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(PaymentException ex) {
        ApiError error = new ApiError(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(PaymentAccessException.class)
    public ResponseEntity<ApiError> handleAccess(PaymentAccessException ex) {
        HttpStatus status = "AUTHENTICATION_REQUIRED".equals(ex.getErrorCode())
                ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(new ApiError(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(com.cognizant.stablecoins.payment.approval.ApprovalConflictException.class)
    public ResponseEntity<ApiError> handleApprovalConflict(com.cognizant.stablecoins.payment.approval.ApprovalConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidPath(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Invalid path or query value"));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyConflictException ex) {
        ApiError error = new ApiError(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(PaymentValidationException.class)
    public ResponseEntity<ApiError> handleBusinessValidation(PaymentValidationException ex) {
        ApiError error = new ApiError(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(com.cognizant.stablecoins.payment.agent.AgentFailure.class)
    public ResponseEntity<ApiError> handleAgent(com.cognizant.stablecoins.payment.agent.AgentFailure ex) {
        HttpStatus status=switch(ex.code()) {
            case "AGENT_DISABLED","AGENT_CUSTOMER_NOT_ENABLED" -> HttpStatus.FORBIDDEN;
            case "AGENT_RUN_NOT_FOUND","AGENT_RECOMMENDATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "AGENT_INVALID_INSTRUCTION","AGENT_INVALID_IDEMPOTENCY_KEY" -> HttpStatus.BAD_REQUEST;
            case "AGENT_CAPACITY_EXHAUSTED","AGENT_PAYMENT_RUN_LIMIT" -> HttpStatus.TOO_MANY_REQUESTS;
            case "AGENT_PROVIDER_NOT_CONFIGURED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ApiError(ex.code(),"Agent request could not proceed"));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        var methods = ex.getSupportedHttpMethods();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(methods == null ? new org.springframework.http.HttpMethod[0] : methods.toArray(org.springframework.http.HttpMethod[]::new))
                .body(new ApiError("METHOD_NOT_ALLOWED", "Use the supported HTTP method for this resource"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("service=payment-service event=unexpected_error", ex);
        ApiError error = new ApiError("INTERNAL_ERROR", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
