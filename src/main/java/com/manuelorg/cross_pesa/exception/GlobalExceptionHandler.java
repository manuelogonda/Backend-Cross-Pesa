package com.manuelorg.cross_pesa.exception;

import com.manuelorg.cross_pesa.exception.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Handle Missing Resources (404 Not Found)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse("Resource not found", HttpStatus.NOT_FOUND, request, null);
    }

    // 2. Handle Bad Business Logic / Invalid States (400 Bad Request)
    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        log.warn("Rejected bad request at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse("Invalid request", HttpStatus.BAD_REQUEST, request, null);
    }

    // 3. Handle Financial Logic Constraints (422 Unprocessable Entity)
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex, HttpServletRequest request) {
        log.warn("Rejected insufficient balance request at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse("Insufficient balance", HttpStatus.UNPROCESSABLE_CONTENT, request, null);
    }

    // 4. Handle Incorrect Passwords from Spring Security (401 Unauthorized)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Rejected authentication request at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse("Authentication failed", HttpStatus.UNAUTHORIZED, request, null);
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ErrorResponse> handleDisabledOrLocked(RuntimeException ex, HttpServletRequest request) {
        log.warn("Rejected account-state authentication request at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse("Authentication failed", HttpStatus.UNAUTHORIZED, request, null);
    }

    // 4b. Handle duplicate idempotency keys (409 Conflict)
    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTransaction(DuplicateTransactionException ex, HttpServletRequest request) {
        log.info("Duplicate transaction request at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse("Duplicate transaction request", HttpStatus.CONFLICT, request, null);
    }

    // 5. Handle DTO @Valid Failures (e.g., weak password, invalid email format)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();

        // Loop through all failed fields and extract the custom messages we wrote in the DTO
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return buildErrorResponse("Validation failed for one or more fields", HttpStatus.BAD_REQUEST, request, errors);
    }

    // 6. The Ultimate Catch-All for unexpected server crashes (500 Internal Server Error)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse("An unexpected internal server error occurred", HttpStatus.INTERNAL_SERVER_ERROR, request, null);
    }

    // Helper Method to assemble the JSON structure
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            String message,
            HttpStatus status,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }
//    7. rate limiting
    // Catch our custom Rate Limit Exception
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException ex) {

        // Build a clean JSON response for the React frontend
        Map<String, Object> errorResponse = Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "error", "Too Many Requests",
                "message", ex.getMessage()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.TOO_MANY_REQUESTS);
    }

}
