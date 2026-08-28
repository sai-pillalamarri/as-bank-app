package com.asbank.account.account;

import com.asbank.account.customer.CustomerClient;
import com.asbank.account.customer.CustomerServiceUnavailableException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountService {

    private static final Set<String> ELEVATED_ROLES =
            Set.of("ROLE_OPERATIONS", "ROLE_ADMIN");

    private final AccountRepository repository;
    private final CustomerClient customerClient;
    private final AccountMetrics metrics;

    public AccountService(
            AccountRepository repository,
            CustomerClient customerClient,
            AccountMetrics metrics
    ) {
        this.repository = repository;
        this.customerClient = customerClient;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
            "hasAuthority('SCOPE_account.read') and " +
                    "hasAnyRole('CUSTOMER', 'OPERATIONS', 'ADMIN')"
    )
    public AccountResponse getAccount(
            UUID accountId,
            String accessToken,
            String correlationId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        Account account = repository.findById(accountId)
                .orElseThrow(() -> {
                    metrics.notFound();
                    return new AccountNotFoundException();
                });

        verifyCustomerAccess(
                account.getCustomerId(),
                accessToken,
                correlationId,
                authorities
        );

        metrics.success();

        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
            "hasAuthority('SCOPE_account.read') and " +
                    "hasAnyRole('CUSTOMER', 'OPERATIONS', 'ADMIN')"
    )
    public PagedResponse<AccountResponse> getAccountsForCustomer(
            UUID customerId,
            Pageable pageable,
            String accessToken,
            String correlationId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        verifyCustomerAccess(
                customerId,
                accessToken,
                correlationId,
                authorities
        );

        Page<AccountResponse> accounts = repository
                .findByCustomerId(customerId, pageable)
                .map(AccountResponse::from);

        metrics.success();

        return PagedResponse.from(accounts);
    }

    private void verifyCustomerAccess(
            UUID customerId,
            String accessToken,
            String correlationId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        if (hasElevatedRole(authorities)) {
            return;
        }

        try {
            customerClient.assertReadable(
                    customerId,
                    accessToken,
                    correlationId
            );
        } catch (AccessDeniedException exception) {
            metrics.forbidden();
            throw exception;
        } catch (CustomerServiceUnavailableException exception) {
            metrics.downstreamFailure();
            throw exception;
        }
    }

    private boolean hasElevatedRole(
            Collection<? extends GrantedAuthority> authorities
    ) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ELEVATED_ROLES::contains);
    }
}