package com.orders.infrastructure.concurrency;

public class OptimisticConcurrencyException extends RuntimeException {
    public OptimisticConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
