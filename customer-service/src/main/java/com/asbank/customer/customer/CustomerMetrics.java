package com.asbank.customer.customer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class CustomerMetrics {

    private final Counter successfulLookups;
    private final Counter missingLookups;
    private final Counter forbiddenLookups;

    CustomerMetrics(MeterRegistry registry) {
        this.successfulLookups = Counter.builder("asbank.customer.lookups")
                .tag("result", "success")
                .register(registry);

        this.missingLookups = Counter.builder("asbank.customer.lookups")
                .tag("result", "not_found")
                .register(registry);

        this.forbiddenLookups = Counter.builder("asbank.customer.lookups")
                .tag("result", "forbidden")
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
}