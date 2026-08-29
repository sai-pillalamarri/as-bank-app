package com.asbank.transaction.account;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceCommandRequest(
        UUID commandId,
        String type,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency
) {
}