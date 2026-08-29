package com.asbank.transaction.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(
            UUID transactionId,
            UUID accountId,
            LedgerDirection direction,
            BigDecimal amount,
            String currency
    ) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    static LedgerEntry debit(
            BankTransaction transaction,
            UUID accountId
    ) {
        return new LedgerEntry(
                transaction.getId(),
                accountId,
                LedgerDirection.DEBIT,
                transaction.getAmount(),
                transaction.getCurrency()
        );
    }

    static LedgerEntry credit(
            BankTransaction transaction,
            UUID accountId
    ) {
        return new LedgerEntry(
                transaction.getId(),
                accountId,
                LedgerDirection.CREDIT,
                transaction.getAmount(),
                transaction.getCurrency()
        );
    }

    UUID getId() {
        return id;
    }

    UUID getTransactionId() {
        return transactionId;
    }

    UUID getAccountId() {
        return accountId;
    }

    LedgerDirection getDirection() {
        return direction;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}