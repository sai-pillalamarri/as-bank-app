package com.asbank.transaction.transaction;

public class IdempotencyConflictException
        extends RuntimeException {

    public IdempotencyConflictException() {
        super(
                "Idempotency key was already used " +
                        "for a different transaction"
        );
    }
}