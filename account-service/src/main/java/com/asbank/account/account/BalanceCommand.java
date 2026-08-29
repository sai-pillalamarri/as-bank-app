package com.asbank.account.account;

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
@Table(name = "balance_commands")
class BalanceCommand {

    @Id
    @Column(name = "command_id")
    private UUID commandId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false)
    private BalanceCommandType type;

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
    private BalanceCommandStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private BalanceFailureReason failureReason;

    @Column(
            name = "source_balance_after",
            precision = 19,
            scale = 2
    )
    private BigDecimal sourceBalanceAfter;

    @Column(
            name = "destination_balance_after",
            precision = 19,
            scale = 2
    )
    private BigDecimal destinationBalanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BalanceCommand() {
    }

    private BalanceCommand(
            BalanceCommandRequest request,
            BalanceCommandStatus status,
            BalanceFailureReason failureReason,
            BigDecimal sourceBalanceAfter,
            BigDecimal destinationBalanceAfter
    ) {
        this.commandId = request.commandId();
        this.type = request.type();
        this.sourceAccountId = request.sourceAccountId();
        this.destinationAccountId = request.destinationAccountId();
        this.amount = request.amount();
        this.currency = request.currency();
        this.status = status;
        this.failureReason = failureReason;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.destinationBalanceAfter = destinationBalanceAfter;
        this.createdAt = Instant.now();
    }

    static BalanceCommand applied(
            BalanceCommandRequest request,
            BigDecimal sourceBalanceAfter,
            BigDecimal destinationBalanceAfter
    ) {
        return new BalanceCommand(
                request,
                BalanceCommandStatus.APPLIED,
                null,
                sourceBalanceAfter,
                destinationBalanceAfter
        );
    }

    static BalanceCommand rejected(
            BalanceCommandRequest request,
            BalanceFailureReason reason,
            BigDecimal sourceBalanceAfter,
            BigDecimal destinationBalanceAfter
    ) {
        return new BalanceCommand(
                request,
                BalanceCommandStatus.REJECTED,
                reason,
                sourceBalanceAfter,
                destinationBalanceAfter
        );
    }

    boolean matches(BalanceCommandRequest request) {
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

    BalanceCommandResponse toResponse() {
        return new BalanceCommandResponse(
                commandId,
                type,
                status,
                failureReason,
                sourceBalanceAfter,
                destinationBalanceAfter
        );
    }
}