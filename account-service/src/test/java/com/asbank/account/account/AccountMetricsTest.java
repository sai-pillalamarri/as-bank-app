package com.asbank.account.account;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountMetricsTest {

    @Test
    void recordsAccountLookupOutcomesAndDownstreamFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AccountMetrics metrics = new AccountMetrics(registry);

        metrics.success();
        metrics.notFound();
        metrics.forbidden();
        metrics.downstreamFailure();

        assertEquals(
                1.0,
                registry.get("asbank.account.lookups")
                        .tag("result", "success")
                        .counter()
                        .count()
        );

        assertEquals(
                1.0,
                registry.get("asbank.account.lookups")
                        .tag("result", "not_found")
                        .counter()
                        .count()
        );

        assertEquals(
                1.0,
                registry.get("asbank.account.lookups")
                        .tag("result", "forbidden")
                        .counter()
                        .count()
        );

        assertEquals(
                1.0,
                registry.get("asbank.downstream.calls")
                        .tag("target", "customer-service")
                        .tag("outcome", "failure")
                        .counter()
                        .count()
        );
    }
}