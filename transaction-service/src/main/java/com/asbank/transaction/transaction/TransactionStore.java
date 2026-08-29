package com.asbank.transaction.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
class TransactionStore {

    private final BankTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerRepository;

    TransactionStore(
            BankTransactionRepository transactionRepository,
            LedgerEntryRepository ledgerRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public BankTransaction createPending(
            String idempotencyKey,
            TransactionRequest request
    ) {
        BankTransaction transaction =
                BankTransaction.pending(
                        idempotencyKey,
                        request
                );

        return transactionRepository.saveAndFlush(transaction);
    }

    @Transactional(readOnly = true)
    public Optional<BankTransaction> findByIdempotencyKey(
            String idempotencyKey
    ) {
        return transactionRepository.findByIdempotencyKey(
                idempotencyKey
        );
    }

    @Transactional
    public TransactionResponse complete(
            UUID transactionId,
            BalanceCommandResult result
    ) {
        BankTransaction transaction = transactionRepository
                .findByIdForUpdate(transactionId)
                .orElseThrow();

        if (transaction.terminal()) {
            return TransactionResponse.from(transaction);
        }

        if (result.applied()) {
            transaction.applied();

            appendLedger(transaction);
        } else {
            transaction.rejected(
                    TransactionFailureReason.valueOf(
                            result.failureReason()
                    )
            );
        }

        return TransactionResponse.from(transaction);
    }

    private void appendLedger(BankTransaction transaction) {
        if (ledgerRepository.existsByTransactionId(
                transaction.getId()
        )) {
            return;
        }

        switch (transaction.getType()) {
            case TRANSFER -> {
                ledgerRepository.save(
                        LedgerEntry.debit(
                                transaction,
                                transaction.getSourceAccountId()
                        )
                );

                ledgerRepository.save(
                        LedgerEntry.credit(
                                transaction,
                                transaction.getDestinationAccountId()
                        )
                );
            }

            case DEPOSIT ->
                    ledgerRepository.save(
                            LedgerEntry.credit(
                                    transaction,
                                    transaction.getDestinationAccountId()
                            )
                    );

            case WITHDRAWAL ->
                    ledgerRepository.save(
                            LedgerEntry.debit(
                                    transaction,
                                    transaction.getSourceAccountId()
                            )
                    );
        }
    }
}