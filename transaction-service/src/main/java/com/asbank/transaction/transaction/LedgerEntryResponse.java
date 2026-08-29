package com.asbank.transaction.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transactionId,
        UUID accountId,
        LedgerDirection direction,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {

    static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransactionId(),
                entry.getAccountId(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getCreatedAt()
        );
    }
}