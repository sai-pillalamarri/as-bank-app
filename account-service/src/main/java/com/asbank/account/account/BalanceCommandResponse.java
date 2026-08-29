package com.asbank.account.account;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceCommandResponse(
        UUID commandId,
        BalanceCommandType type,
        BalanceCommandStatus status,
        BalanceFailureReason failureReason,
        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceAfter
) {
}