package com.asbank.account.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
class BalanceCommandExecutor {

    private final AccountRepository accountRepository;
    private final BalanceCommandRepository commandRepository;

    BalanceCommandExecutor(
            AccountRepository accountRepository,
            BalanceCommandRepository commandRepository
    ) {
        this.accountRepository = accountRepository;
        this.commandRepository = commandRepository;
    }

    @Transactional
    public BalanceCommandResponse execute(
            BalanceCommandRequest request
    ) {
        Optional<BalanceCommand> existing =
                commandRepository.findById(request.commandId());

        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        return switch (request.type()) {
            case TRANSFER -> transfer(request);
            case DEPOSIT -> deposit(request);
            case WITHDRAWAL -> withdrawal(request);
        };
    }

    private BalanceCommandResponse transfer(
            BalanceCommandRequest request
    ) {
        if (request.sourceAccountId() == null
                || request.destinationAccountId() == null) {
            return reject(
                    request,
                    BalanceFailureReason.INVALID_ACCOUNT_SELECTION,
                    null,
                    null
            );
        }

        if (request.sourceAccountId()
                .equals(request.destinationAccountId())) {
            return reject(
                    request,
                    BalanceFailureReason.SAME_ACCOUNT,
                    null,
                    null
            );
        }

        Account source = accountRepository
                .findById(request.sourceAccountId())
                .orElse(null);

        Account destination = accountRepository
                .findById(request.destinationAccountId())
                .orElse(null);

        if (source == null || destination == null) {
            return reject(
                    request,
                    BalanceFailureReason.ACCOUNT_NOT_FOUND,
                    balance(source),
                    balance(destination)
            );
        }

        BalanceFailureReason stateFailure =
                accountStateFailure(source, destination);

        if (stateFailure != null) {
            return reject(
                    request,
                    stateFailure,
                    source.getBalance(),
                    destination.getBalance()
            );
        }

        if (!currencyMatches(request, source)
                || !currencyMatches(request, destination)) {
            return reject(
                    request,
                    BalanceFailureReason.CURRENCY_MISMATCH,
                    source.getBalance(),
                    destination.getBalance()
            );
        }

        if (source.getBalance().compareTo(request.amount()) < 0) {
            return reject(
                    request,
                    BalanceFailureReason.INSUFFICIENT_FUNDS,
                    source.getBalance(),
                    destination.getBalance()
            );
        }

        source.debit(request.amount());
        destination.credit(request.amount());

        return applied(
                request,
                source.getBalance(),
                destination.getBalance()
        );
    }

    private BalanceCommandResponse deposit(
            BalanceCommandRequest request
    ) {
        if (request.sourceAccountId() != null
                || request.destinationAccountId() == null) {
            return reject(
                    request,
                    BalanceFailureReason.INVALID_ACCOUNT_SELECTION,
                    null,
                    null
            );
        }

        Account destination = accountRepository
                .findById(request.destinationAccountId())
                .orElse(null);

        if (destination == null) {
            return reject(
                    request,
                    BalanceFailureReason.ACCOUNT_NOT_FOUND,
                    null,
                    null
            );
        }

        BalanceFailureReason stateFailure =
                accountStateFailure(destination);

        if (stateFailure != null) {
            return reject(
                    request,
                    stateFailure,
                    null,
                    destination.getBalance()
            );
        }

        if (!currencyMatches(request, destination)) {
            return reject(
                    request,
                    BalanceFailureReason.CURRENCY_MISMATCH,
                    null,
                    destination.getBalance()
            );
        }

        destination.credit(request.amount());

        return applied(
                request,
                null,
                destination.getBalance()
        );
    }

    private BalanceCommandResponse withdrawal(
            BalanceCommandRequest request
    ) {
        if (request.sourceAccountId() == null
                || request.destinationAccountId() != null) {
            return reject(
                    request,
                    BalanceFailureReason.INVALID_ACCOUNT_SELECTION,
                    null,
                    null
            );
        }

        Account source = accountRepository
                .findById(request.sourceAccountId())
                .orElse(null);

        if (source == null) {
            return reject(
                    request,
                    BalanceFailureReason.ACCOUNT_NOT_FOUND,
                    null,
                    null
            );
        }

        BalanceFailureReason stateFailure =
                accountStateFailure(source);

        if (stateFailure != null) {
            return reject(
                    request,
                    stateFailure,
                    source.getBalance(),
                    null
            );
        }

        if (!currencyMatches(request, source)) {
            return reject(
                    request,
                    BalanceFailureReason.CURRENCY_MISMATCH,
                    source.getBalance(),
                    null
            );
        }

        if (source.getBalance().compareTo(request.amount()) < 0) {
            return reject(
                    request,
                    BalanceFailureReason.INSUFFICIENT_FUNDS,
                    source.getBalance(),
                    null
            );
        }

        source.debit(request.amount());

        return applied(
                request,
                source.getBalance(),
                null
        );
    }

    private BalanceCommandResponse replay(
            BalanceCommand existing,
            BalanceCommandRequest request
    ) {
        if (existing.matches(request)) {
            return existing.toResponse();
        }

        return new BalanceCommandResponse(
                request.commandId(),
                request.type(),
                BalanceCommandStatus.REJECTED,
                BalanceFailureReason.IDEMPOTENCY_CONFLICT,
                null,
                null
        );
    }

    private BalanceCommandResponse applied(
            BalanceCommandRequest request,
            BigDecimal sourceBalance,
            BigDecimal destinationBalance
    ) {
        BalanceCommand command = BalanceCommand.applied(
                request,
                sourceBalance,
                destinationBalance
        );

        commandRepository.save(command);

        return command.toResponse();
    }

    private BalanceCommandResponse reject(
            BalanceCommandRequest request,
            BalanceFailureReason reason,
            BigDecimal sourceBalance,
            BigDecimal destinationBalance
    ) {
        BalanceCommand command = BalanceCommand.rejected(
                request,
                reason,
                sourceBalance,
                destinationBalance
        );

        commandRepository.save(command);

        return command.toResponse();
    }

    private boolean currencyMatches(
            BalanceCommandRequest request,
            Account account
    ) {
        return account.getCurrency().equals(request.currency());
    }

    private BigDecimal balance(Account account) {
        return account == null ? null : account.getBalance();
    }

    private BalanceFailureReason accountStateFailure(
            Account... accounts
    ) {
        for (Account account : accounts) {
            if (account.getStatus() == AccountStatus.FROZEN) {
                return BalanceFailureReason.ACCOUNT_FROZEN;
            }

            if (account.getStatus() == AccountStatus.CLOSED) {
                return BalanceFailureReason.ACCOUNT_CLOSED;
            }
        }

        return null;
    }
}