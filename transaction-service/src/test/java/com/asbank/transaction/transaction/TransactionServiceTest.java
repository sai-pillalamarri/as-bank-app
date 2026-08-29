package com.asbank.transaction.transaction;

import com.asbank.transaction.account.AccountClient;
import com.asbank.transaction.account.BalanceCommandRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

    private static final UUID SOURCE_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID DESTINATION_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private TransactionStore store;
    private LedgerEntryRepository ledgerRepository;
    private AccountClient accountClient;
    private TransactionMetrics metrics;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        store = mock(TransactionStore.class);
        ledgerRepository =
                mock(LedgerEntryRepository.class);
        accountClient = mock(AccountClient.class);
        metrics = mock(TransactionMetrics.class);

        service = new TransactionService(
                store,
                ledgerRepository,
                accountClient,
                metrics
        );
    }

    @Test
    void usesTransactionIdAsStableAccountCommandId() {
        TransferRequest request =
                new TransferRequest(
                        SOURCE_ID,
                        DESTINATION_ID,
                        new BigDecimal("100.00"),
                        "GBP"
                );

        BankTransaction transaction =
                BankTransaction.pending(
                        "transfer-1",
                        new TransactionRequest(
                                TransactionType.TRANSFER,
                                SOURCE_ID,
                                DESTINATION_ID,
                                new BigDecimal("100.00"),
                                "GBP"
                        )
                );

        when(
                store.findByIdempotencyKey("transfer-1")
        ).thenReturn(Optional.empty());

        when(
                store.createPending(
                        eq("transfer-1"),
                        any(TransactionRequest.class)
                )
        ).thenReturn(transaction);

        when(
                accountClient.apply(
                        any(BalanceCommandRequest.class),
                        eq("token"),
                        eq("correlation-1")
                )
        ).thenReturn(
                new BalanceCommandResult(
                        "APPLIED",
                        null
                )
        );

        TransactionResponse completed =
                new TransactionResponse(
                        transaction.getId(),
                        TransactionType.TRANSFER,
                        TransactionStatus.APPLIED,
                        null,
                        SOURCE_ID,
                        DESTINATION_ID,
                        new BigDecimal("100.00"),
                        "GBP",
                        transaction.getCreatedAt(),
                        Instant.now()
                );

        when(
                store.complete(
                        eq(transaction.getId()),
                        any(BalanceCommandResult.class)
                )
        ).thenReturn(completed);

        TransactionResponse response =
                service.transfer(
                        "transfer-1",
                        request,
                        "token",
                        "correlation-1"
                );

        assertEquals(
                TransactionStatus.APPLIED,
                response.status()
        );

        ArgumentCaptor<BalanceCommandRequest> captor =
                ArgumentCaptor.forClass(
                        BalanceCommandRequest.class
                );

        verify(accountClient).apply(
                captor.capture(),
                eq("token"),
                eq("correlation-1")
        );

        assertEquals(
                transaction.getId(),
                captor.getValue().commandId()
        );
    }

    @Test
    void returnsCompletedTransactionOnIdempotentReplay() {
        BankTransaction transaction =
                BankTransaction.pending(
                        "transfer-2",
                        new TransactionRequest(
                                TransactionType.TRANSFER,
                                SOURCE_ID,
                                DESTINATION_ID,
                                new BigDecimal("100.00"),
                                "GBP"
                        )
                );

        transaction.applied();

        when(
                store.findByIdempotencyKey("transfer-2")
        ).thenReturn(Optional.of(transaction));

        TransactionResponse response =
                service.transfer(
                        "transfer-2",
                        new TransferRequest(
                                SOURCE_ID,
                                DESTINATION_ID,
                                new BigDecimal("100.00"),
                                "GBP"
                        ),
                        "token",
                        "correlation-2"
                );

        assertEquals(
                TransactionStatus.APPLIED,
                response.status()
        );

        verify(accountClient).assertReadable(
                SOURCE_ID,
                "token",
                "correlation-2"
        );

        verify(
                store,
                never()
        ).createPending(
                any(),
                any()
        );
    }

    @Test
    void rejectsIdempotencyKeyUsedForDifferentTransfer() {
        BankTransaction existing =
                BankTransaction.pending(
                        "transfer-3",
                        new TransactionRequest(
                                TransactionType.TRANSFER,
                                SOURCE_ID,
                                DESTINATION_ID,
                                new BigDecimal("100.00"),
                                "GBP"
                        )
                );

        when(
                store.findByIdempotencyKey("transfer-3")
        ).thenReturn(Optional.of(existing));

        assertThrows(
                IdempotencyConflictException.class,
                () -> service.transfer(
                        "transfer-3",
                        new TransferRequest(
                                SOURCE_ID,
                                DESTINATION_ID,
                                new BigDecimal("200.00"),
                                "GBP"
                        ),
                        "token",
                        "correlation-3"
                )
        );

        verifyNoInteractions(accountClient);
    }

    @Test
    void verifiesAccountOwnershipBeforeReturningHistory() {
        LedgerEntry entry = mock(LedgerEntry.class);

        when(entry.getId())
                .thenReturn(UUID.randomUUID());

        when(entry.getTransactionId())
                .thenReturn(UUID.randomUUID());

        when(entry.getAccountId())
                .thenReturn(SOURCE_ID);

        when(entry.getDirection())
                .thenReturn(LedgerDirection.DEBIT);

        when(entry.getAmount())
                .thenReturn(new BigDecimal("10.00"));

        when(entry.getCurrency())
                .thenReturn("GBP");

        when(entry.getCreatedAt())
                .thenReturn(Instant.now());

        PageRequest pageable =
                PageRequest.of(0, 20);

        when(
                ledgerRepository.findByAccountId(
                        SOURCE_ID,
                        pageable
                )
        ).thenReturn(
                new PageImpl<>(
                        List.of(entry),
                        pageable,
                        1
                )
        );

        PagedResponse<LedgerEntryResponse> response =
                service.history(
                        SOURCE_ID,
                        pageable,
                        "token",
                        "correlation-4"
                );

        assertEquals(
                1,
                response.totalElements()
        );

        verify(accountClient).assertReadable(
                SOURCE_ID,
                "token",
                "correlation-4"
        );
    }
}