package com.asbank.transaction.transaction;

import java.math.BigDecimal;
import java.util.UUID;

record TransactionRequest(
        TransactionType type,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency
) {
}