package com.asbank.transaction.transaction;

public enum TransactionFailureReason {
    ACCOUNT_NOT_FOUND,
    ACCOUNT_FROZEN,
    ACCOUNT_CLOSED,
    INSUFFICIENT_FUNDS,
    CURRENCY_MISMATCH,
    SAME_ACCOUNT,
    INVALID_ACCOUNT_SELECTION,
    IDEMPOTENCY_CONFLICT
}