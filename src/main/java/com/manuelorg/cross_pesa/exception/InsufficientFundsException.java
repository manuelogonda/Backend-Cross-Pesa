package com.manuelorg.cross_pesa.exception;

/**
 * Thrown when a money movement would take a retail wallet below zero.
 * Maps to HTTP 409 CONFLICT via GlobalExceptionHandler semantics.
 */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
