package com.asbank.account.account;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID customerId,
        String accountNumber,
        AccountType type,
        AccountStatus status,
        BigDecimal balance,
        String currency,
        long version
) {

    static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCustomerId(),
                account.getAccountNumber(),
                account.getType(),
                account.getStatus(),
                account.getBalance(),
                account.getCurrency(),
                account.getVersion()
        );
    }
}