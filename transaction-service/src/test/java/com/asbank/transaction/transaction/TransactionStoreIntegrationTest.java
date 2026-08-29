package com.asbank.transaction.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Testcontainers
@Import(TransactionStore.class)
class TransactionStoreIntegrationTest {

    private static final UUID SOURCE_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID DESTINATION_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private TransactionStore store;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );

        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration"
        );
    }

    @Test
    void completesTransferAndAppendsLedgerOnce() {
        TransactionRequest request =
                new TransactionRequest(
                        TransactionType.TRANSFER,
                        SOURCE_ID,
                        DESTINATION_ID,
                        new BigDecimal("100.00"),
                        "GBP"
                );

        BankTransaction transaction =
                store.createPending(
                        "transfer-test-1",
                        request
                );

        TransactionResponse response = store.complete(
                transaction.getId(),
                new BalanceCommandResult(
                        "APPLIED",
                        null
                )
        );

        assertEquals(
                TransactionStatus.APPLIED,
                response.status()
        );

        assertEquals(
                2,
                ledgerRepository.count()
        );

        Set<LedgerDirection> directions =
                ledgerRepository.findAll()
                        .stream()
                        .map(LedgerEntry::getDirection)
                        .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        LedgerDirection.DEBIT,
                        LedgerDirection.CREDIT
                ),
                directions
        );

        store.complete(
                transaction.getId(),
                new BalanceCommandResult(
                        "APPLIED",
                        null
                )
        );

        assertEquals(
                2,
                ledgerRepository.count()
        );
    }

    @Test
    void rejectedTransactionDoesNotCreateLedgerEntries() {
        TransactionRequest request =
                new TransactionRequest(
                        TransactionType.WITHDRAWAL,
                        SOURCE_ID,
                        null,
                        new BigDecimal("5000.00"),
                        "GBP"
                );

        BankTransaction transaction =
                store.createPending(
                        "withdrawal-test-1",
                        request
                );

        TransactionResponse response = store.complete(
                transaction.getId(),
                new BalanceCommandResult(
                        "REJECTED",
                        "INSUFFICIENT_FUNDS"
                )
        );

        assertEquals(
                TransactionStatus.REJECTED,
                response.status()
        );

        assertEquals(
                TransactionFailureReason.INSUFFICIENT_FUNDS,
                response.failureReason()
        );

        assertEquals(
                0,
                ledgerRepository.count()
        );
    }
}