package com.asbank.transaction.transaction;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionMetricsTest {

    @Test
    void recordsTransferResult() {
        SimpleMeterRegistry registry =
                new SimpleMeterRegistry();

        TransactionMetrics metrics =
                new TransactionMetrics(registry);

        metrics.recordTransfer("success");

        assertEquals(
                1.0,
                registry.get("asbank.transfers")
                        .tag("result", "success")
                        .counter()
                        .count()
        );
    }

    @Test
    void recordsTransferDuration() {
        SimpleMeterRegistry registry =
                new SimpleMeterRegistry();

        TransactionMetrics metrics =
                new TransactionMetrics(registry);

        metrics.recordTransferDuration(
                TimeUnit.MILLISECONDS.toNanos(100)
        );

        assertEquals(
                1,
                registry.get("asbank.transfer.duration")
                        .timer()
                        .count()
        );
    }

    @Test
    void recordsDownstreamResult() {
        SimpleMeterRegistry registry =
                new SimpleMeterRegistry();

        TransactionMetrics metrics =
                new TransactionMetrics(registry);

        metrics.recordDownstream(
                "account-service",
                "success"
        );

        assertEquals(
                1.0,
                registry.get("asbank.downstream.calls")
                        .tag(
                                "target",
                                "account-service"
                        )
                        .tag(
                                "outcome",
                                "success"
                        )
                        .counter()
                        .count()
        );
    }
}