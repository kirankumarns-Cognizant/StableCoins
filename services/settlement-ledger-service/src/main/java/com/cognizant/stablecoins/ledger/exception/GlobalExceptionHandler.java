package com.cognizant.stablecoins.ledger.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JournalValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(JournalValidationException ex) {
        boolean conflict = ex.getReason().contains("CONFLICT") || ex.getReason().contains("NOT_REPLAYABLE") || ex.getReason().contains("REVIEW_REQUIRED");
        int status = conflict ? 409 : 422;
        return ResponseEntity.status(status).body(Map.of(
            "status", status,
            "error", conflict ? "JOURNAL_CONFLICT" : "JOURNAL_VALIDATION_FAILED",
            "message", ex.getReason(),
            "timestamp", OffsetDateTime.now().toString()
        ));
    }

    @ExceptionHandler(JournalNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(JournalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "status", 404,
            "error", "JOURNAL_NOT_FOUND",
            "message", ex.getMessage(),
            "timestamp", OffsetDateTime.now().toString()
        ));
    }

    @ExceptionHandler(JournalAlreadyReversedException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyReversed(JournalAlreadyReversedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "status", 409,
            "error", "JOURNAL_ALREADY_REVERSED",
            "message", ex.getMessage(),
            "timestamp", OffsetDateTime.now().toString()
        ));
    }

    @ExceptionHandler(LedgerAccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotFound(LedgerAccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
            "status", 422,
            "error", "LEDGER_ACCOUNT_NOT_FOUND",
            "message", ex.getMessage(),
            "timestamp", OffsetDateTime.now().toString()
        ));
    }
}
