package com.asbank.transaction.transaction;

import com.asbank.transaction.account.AccountClient;
import com.asbank.transaction.account.BalanceCommandRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;

@Service
public class TransactionService {

    private final TransactionStore store;
    private final LedgerEntryRepository ledgerRepository;
    private final AccountClient accountClient;
    private final TransactionMetrics metrics;

    public TransactionService(
            TransactionStore store,
            LedgerEntryRepository ledgerRepository,
            AccountClient accountClient,
            TransactionMetrics metrics
    ) {
        this.store = store;
        this.ledgerRepository = ledgerRepository;
        this.accountClient = accountClient;
        this.metrics = metrics;
    }

    @PreAuthorize(
            "hasAuthority('SCOPE_transaction.write') and " +
                    "hasAnyRole('CUSTOMER', 'OPERATIONS', 'ADMIN')"
    )
    public TransactionResponse transfer(
            String idempotencyKey,
            TransferRequest request,
            String accessToken,
            String correlationId
    ) {
        long started = System.nanoTime();

        try {
            return execute(
                    idempotencyKey,
                    new TransactionRequest(
                            TransactionType.TRANSFER,
                            request.sourceAccountId(),
                            request.destinationAccountId(),
                            request.amount(),
                            request.currency()
                    ),
                    accessToken,
                    correlationId
            );
        } finally {
            metrics.recordTransferDuration(
                    System.nanoTime() - started
            );
        }
    }

    @PreAuthorize(
            "hasAuthority('SCOPE_transaction.write') and " +
                    "hasAnyRole('CUSTOMER', 'OPERATIONS', 'ADMIN')"
    )
    public TransactionResponse deposit(
            String idempotencyKey,
            DepositRequest request,
            String accessToken,
            String correlationId
    ) {
        return execute(
                idempotencyKey,
                new TransactionRequest(
                        TransactionType.DEPOSIT,
                        null,
                        request.destinationAccountId(),
                        request.amount(),
                        request.currency()
                ),
                accessToken,
                correlationId
        );
    }

    @PreAuthorize(
            "hasAuthority('SCOPE_transaction.write') and " +
                    "hasAnyRole('CUSTOMER', 'OPERATIONS', 'ADMIN')"
    )
    public TransactionResponse withdrawal(
            String idempotencyKey,
            WithdrawalRequest request,
            String accessToken,
            String correlationId
    ) {
        return execute(
                idempotencyKey,
                new TransactionRequest(
                        TransactionType.WITHDRAWAL,
                        request.sourceAccountId(),
                        null,
                        request.amount(),
                        request.currency()
                ),
                accessToken,
                correlationId
        );
    }

    @PreAuthorize(
            "hasAuthority('SCOPE_transaction.read') and " +
                    "hasAnyRole('CUSTOMER', 'OPERATIONS', 'ADMIN')"
    )
    public PagedResponse<LedgerEntryResponse> history(
            java.util.UUID accountId,
            Pageable pageable,
            String accessToken,
            String correlationId
    ) {
        accountClient.assertReadable(
                accountId,
                accessToken,
                correlationId
        );

        return PagedResponse.from(
                ledgerRepository
                        .findByAccountId(accountId, pageable)
                        .map(LedgerEntryResponse::from)
        );
    }

    private TransactionResponse execute(
            String idempotencyKey,
            TransactionRequest rawRequest,
            String accessToken,
            String correlationId
    ) {
        TransactionRequest request = normalize(rawRequest);

        BankTransaction transaction =
                prepare(idempotencyKey, request);

        if (!transaction.matches(request)) {
            throw new IdempotencyConflictException();
        }

        if (transaction.terminal()) {
            accountClient.assertReadable(
                    ownershipAccountId(transaction),
                    accessToken,
                    correlationId
            );

            return TransactionResponse.from(transaction);
        }

        BalanceCommandResult result;

        try {
            result = accountClient.apply(
                    new BalanceCommandRequest(
                            transaction.getId(),
                            transaction.getType().name(),
                            transaction.getSourceAccountId(),
                            transaction.getDestinationAccountId(),
                            transaction.getAmount(),
                            transaction.getCurrency()
                    ),
                    accessToken,
                    correlationId
            );

            metrics.recordDownstream(
                    "account-service",
                    "success"
            );
        } catch (RuntimeException exception) {
            metrics.recordDownstream(
                    "account-service",
                    "failure"
            );

            throw exception;
        }

        TransactionResponse response =
                store.complete(
                        transaction.getId(),
                        result
                );

        if (response.type() == TransactionType.TRANSFER) {
            metrics.recordTransfer(
                    transferMetricResult(response)
            );
        }

        return response;
    }

    private BankTransaction prepare(
            String idempotencyKey,
            TransactionRequest request
    ) {
        var existing = store.findByIdempotencyKey(
                idempotencyKey
        );

        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return store.createPending(
                    idempotencyKey,
                    request
            );
        } catch (DataIntegrityViolationException exception) {
            return store.findByIdempotencyKey(
                            idempotencyKey
                    )
                    .orElseThrow(() -> exception);
        }
    }

    private TransactionRequest normalize(
            TransactionRequest request
    ) {
        return new TransactionRequest(
                request.type(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount().setScale(
                        2,
                        RoundingMode.UNNECESSARY
                ),
                request.currency()
        );
    }

    private String transferMetricResult(
            TransactionResponse response
    ) {
        if (response.status() == TransactionStatus.APPLIED) {
            return "success";
        }

        return switch (response.failureReason()) {
            case INSUFFICIENT_FUNDS ->
                    "insufficient_funds";

            case ACCOUNT_FROZEN ->
                    "account_frozen";

            default ->
                    "rejected";
        };
    }

    private java.util.UUID ownershipAccountId(
            BankTransaction transaction
    ) {
        return switch (transaction.getType()) {
            case TRANSFER, WITHDRAWAL ->
                    transaction.getSourceAccountId();

            case DEPOSIT ->
                    transaction.getDestinationAccountId();
        };
    }
}