package com.manuelorg.cross_pesa.exception;

/**
 * Thrown when a request arrives with an idempotency key that has already
 * been used. Maps to HTTP 409 Conflict so clients can safely replay the
 * original request's outcome instead of treating it as a server error.
 */
public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException(String message) {
        super(message);
    }
}
