package com.asbank.transaction.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions")
class BankTransaction {

    @Id
    private UUID id;

    @Column(
            name = "idempotency_key",
            nullable = false,
            unique = true,
            length = 128
    )
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private TransactionFailureReason failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected BankTransaction() {
    }

    private BankTransaction(
            UUID id,
            String idempotencyKey,
            TransactionRequest request
    ) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.type = request.type();
        this.sourceAccountId = request.sourceAccountId();
        this.destinationAccountId =
                request.destinationAccountId();
        this.amount = request.amount();
        this.currency = request.currency();
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
    }

    static BankTransaction pending(
            String idempotencyKey,
            TransactionRequest request
    ) {
        return new BankTransaction(
                UUID.randomUUID(),
                idempotencyKey,
                request
        );
    }

    boolean matches(TransactionRequest request) {
        return type == request.type()
                && Objects.equals(
                sourceAccountId,
                request.sourceAccountId()
        )
                && Objects.equals(
                destinationAccountId,
                request.destinationAccountId()
        )
                && amount.compareTo(request.amount()) == 0
                && Objects.equals(currency, request.currency());
    }

    void applied() {
        status = TransactionStatus.APPLIED;
        failureReason = null;
        completedAt = Instant.now();
    }

    void rejected(TransactionFailureReason reason) {
        status = TransactionStatus.REJECTED;
        failureReason = reason;
        completedAt = Instant.now();
    }

    boolean terminal() {
        return status != TransactionStatus.PENDING;
    }

    UUID getId() {
        return id;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    TransactionType getType() {
        return type;
    }

    UUID getSourceAccountId() {
        return sourceAccountId;
    }

    UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    TransactionStatus getStatus() {
        return status;
    }

    TransactionFailureReason getFailureReason() {
        return failureReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}