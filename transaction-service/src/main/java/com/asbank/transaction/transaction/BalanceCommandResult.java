package com.asbank.transaction.transaction;

public record BalanceCommandResult(
        String status,
        String failureReason
) {

    public boolean applied() {
        return "APPLIED".equals(status);
    }
}