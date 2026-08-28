package com.asbank.account.customer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CustomerServiceUnavailableExceptionTest {

    @Test
    void preservesMessage() {
        CustomerServiceUnavailableException exception =
                new CustomerServiceUnavailableException(
                        "Customer service unavailable"
                );

        assertEquals(
                "Customer service unavailable",
                exception.getMessage()
        );
    }

    @Test
    void preservesMessageAndCause() {
        RuntimeException cause =
                new RuntimeException("Connection failed");

        CustomerServiceUnavailableException exception =
                new CustomerServiceUnavailableException(
                        "Customer service unavailable",
                        cause
                );

        assertEquals(
                "Customer service unavailable",
                exception.getMessage()
        );

        assertSame(cause, exception.getCause());
    }
}