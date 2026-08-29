package com.asbank.transaction.transaction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
class TransactionMetrics {

    private final MeterRegistry registry;

    TransactionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void recordTransfer(String result) {
        Counter.builder("asbank.transfers")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    void recordTransferDuration(long nanoseconds) {
        Timer.builder("asbank.transfer.duration")
                .register(registry)
                .record(
                        nanoseconds,
                        TimeUnit.NANOSECONDS
                );
    }

    void recordDownstream(
            String target,
            String outcome
    ) {
        Counter.builder("asbank.downstream.calls")
                .tag("target", target)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}