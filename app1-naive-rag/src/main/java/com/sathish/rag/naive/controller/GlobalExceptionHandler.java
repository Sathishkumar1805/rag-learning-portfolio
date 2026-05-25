/**
 * GlobalExceptionHandler.java
 *
 * <p><b>RAG Role:</b> Cross-cutting concern — maps exceptions to structured HTTP responses.
 * Prevents Spring's default whitelabel error page from leaking stack traces to API clients.
 *
 * <p><b>Learning Note:</b>
 * Proper error handling in RAG APIs is critical for usability:
 * - 400: bad request (validation failures, empty questions)
 * - 429: rate limit exceeded (Gemini free tier: 15 RPM)
 * - 503: upstream LLM/embedding service unavailable
 * The 429 case is especially important for demo apps — Gemini's free tier is generous
 * but the Naive RAG pipeline makes 1 embedding call per query, so fast UI clicking
 * can exhaust the quota.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler for the Naive RAG REST API.
 *
 * <p>All @ExceptionHandler methods return a consistent JSON error envelope:
 * <pre>
 * {
 *   "error": "Error type",
 *   "message": "Human-readable description",
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles @Valid bean validation failures (e.g., blank question, chunk size out of range).
     *
     * @param ex the validation exception with field-level errors
     * @return 400 Bad Request with a map of field -> error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(fieldName, message);
        });

        Map<String, Object> body = Map.of(
                "error", "Validation Failed",
                "fields", fieldErrors,
                "timestamp", Instant.now().toString()
        );

        log.warn("Validation failure: {}", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles Gemini rate limit errors (HTTP 429 from the upstream API).
     * Gemini free tier: 15 RPM for Flash, 1500 RPM for embeddings.
     * When you hit the limit, the Spring AI client throws a RuntimeException
     * wrapping the 429 response.
     *
     * @param ex the upstream rate-limit exception
     * @return 429 Too Many Requests with retry guidance
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        // Detect Gemini rate-limit errors by message content
        // (Spring AI wraps upstream HTTP errors as RuntimeException)
        if (ex.getMessage() != null && (ex.getMessage().contains("429")
                || ex.getMessage().toLowerCase().contains("rate limit")
                || ex.getMessage().toLowerCase().contains("quota"))) {

            log.warn("Gemini rate limit hit: {}", ex.getMessage());
            Map<String, Object> body = Map.of(
                    "error", "Rate Limit Exceeded",
                    "message", "Gemini free tier limit reached (15 RPM for Flash, 1500 RPM for embeddings). Wait 60 seconds and retry.",
                    "tip", "For higher throughput, use Groq free tier (6000 RPM) or upgrade to Gemini paid tier.",
                    "timestamp", Instant.now().toString()
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
        }

        // Generic 500 for other runtime errors — log full stack for debugging
        log.error("Unexpected error in RAG pipeline: {}", ex.getMessage(), ex);
        Map<String, Object> body = Map.of(
                "error", "Internal Server Error",
                "message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.internalServerError().body(body);
    }

    /**
     * Catch-all for any non-RuntimeException errors.
     *
     * @param ex any throwable not caught by more specific handlers
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception in RAG API: {}", ex.getMessage(), ex);
        Map<String, Object> body = Map.of(
                "error", "Internal Server Error",
                "message", "An unexpected error occurred. Check server logs for details.",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.internalServerError().body(body);
    }
}
