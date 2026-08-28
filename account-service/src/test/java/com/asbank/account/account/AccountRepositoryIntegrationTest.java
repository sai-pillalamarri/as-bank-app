package com.asbank.account.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class AccountRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );

        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration"
        );
    }

    @Autowired
    private AccountRepository repository;

    @Test
    void storesAndReadsAccountFromPostgres() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Account account = new Account(
                accountId,
                customerId,
                "TEST-" + accountId.toString().substring(0, 20),
                AccountType.CURRENT,
                AccountStatus.ACTIVE,
                new BigDecimal("125.50"),
                "GBP",
                Instant.parse(
                        "2026-01-01T00:00:00Z"
                )
        );

        repository.saveAndFlush(account);

        Account stored = repository
                .findById(accountId)
                .orElseThrow();

        assertThat(stored.getCustomerId())
                .isEqualTo(customerId);

        assertThat(stored.getBalance())
                .isEqualByComparingTo("125.50");

        assertThat(stored.getVersion())
                .isZero();
    }
}