package com.asbank.account.account;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class AccountMetrics {

    private final Counter successfulLookups;
    private final Counter missingLookups;
    private final Counter forbiddenLookups;
    private final Counter downstreamFailures;

    AccountMetrics(MeterRegistry registry) {
        successfulLookups = Counter.builder("asbank.account.lookups")
                .tag("result", "success")
                .register(registry);

        missingLookups = Counter.builder("asbank.account.lookups")
                .tag("result", "not_found")
                .register(registry);

        forbiddenLookups = Counter.builder("asbank.account.lookups")
                .tag("result", "forbidden")
                .register(registry);

        downstreamFailures = Counter.builder("asbank.downstream.calls")
                .tag("target", "customer-service")
                .tag("outcome", "failure")
                .register(registry);
    }

    void success() {
        successfulLookups.increment();
    }

    void notFound() {
        missingLookups.increment();
    }

    void forbidden() {
        forbiddenLookups.increment();
    }

    void downstreamFailure() {
        downstreamFailures.increment();
    }
}