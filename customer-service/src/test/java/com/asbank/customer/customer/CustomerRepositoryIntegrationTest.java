package com.asbank.customer.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class CustomerRepositoryIntegrationTest {

    private static final UUID CUSTOMER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CustomerRepository repository;

    @Test
    void flywayCreatesAndSeedsCustomer() {
        Customer customer = repository.findById(CUSTOMER_ID)
                .orElseThrow();

        assertThat(customer.getSubject())
                .isEqualTo("customer-local-001");

        assertThat(customer.getStatus())
                .isEqualTo(CustomerStatus.ACTIVE);
    }
}