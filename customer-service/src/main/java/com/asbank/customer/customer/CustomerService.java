package com.asbank.customer.customer;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerService {

    private static final Set<String> ELEVATED_ROLES =
            Set.of("ROLE_OPERATIONS", "ROLE_ADMIN");

    private final CustomerRepository repository;
    private final CustomerMetrics metrics;

    public CustomerService(
            CustomerRepository repository,
            CustomerMetrics metrics
    ) {
        this.repository = repository;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
            "hasAuthority('SCOPE_customer.read') and " +
                    "hasAnyRole('CUSTOMER', 'OPERATIONS', 'ADMIN')"
    )
    public CustomerResponse getCustomer(
            UUID customerId,
            String subject,
            Collection<? extends GrantedAuthority> authorities
    ) {
        Customer customer = repository.findById(customerId)
                .orElseThrow(() -> {
                    metrics.notFound();
                    return new CustomerNotFoundException();
                });

        if (!canRead(customer, subject, authorities)) {
            metrics.forbidden();
            throw new AccessDeniedException("Customer access denied");
        }

        metrics.success();

        return CustomerResponse.from(customer);
    }

    private boolean canRead(
            Customer customer,
            String subject,
            Collection<? extends GrantedAuthority> authorities
    ) {
        boolean elevated = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ELEVATED_ROLES::contains);

        return elevated || customer.getSubject().equals(subject);
    }
}