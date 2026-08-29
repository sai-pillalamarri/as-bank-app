package com.asbank.account.account;

import com.asbank.account.customer.CustomerClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@Service
public class BalanceCommandService {

    private static final Set<String> ELEVATED_ROLES =
            Set.of("ROLE_OPERATIONS", "ROLE_ADMIN");

    private final AccountRepository accountRepository;
    private final BalanceCommandRepository commandRepository;
    private final BalanceCommandExecutor executor;
    private final CustomerClient customerClient;

    public BalanceCommandService(
            AccountRepository accountRepository,
            BalanceCommandRepository commandRepository,
            BalanceCommandExecutor executor,
            CustomerClient customerClient
    ) {
        this.accountRepository = accountRepository;
        this.commandRepository = commandRepository;
        this.executor = executor;
        this.customerClient = customerClient;
    }

    @PreAuthorize(
            "hasAuthority('SCOPE_account.write') and " +
                    "hasAnyRole('CUSTOMER', 'OPERATIONS', 'ADMIN')"
    )
    public BalanceCommandResponse apply(
            BalanceCommandRequest request,
            String accessToken,
            String correlationId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        BalanceCommandRequest normalized = normalize(request);

        Optional<BalanceCommand> existing =
                commandRepository.findById(normalized.commandId());

        if (existing.isPresent()) {
            if (existing.get().matches(normalized)) {
                return existing.get().toResponse();
            }

            return idempotencyConflict(normalized);
        }

        if (!hasElevatedRole(authorities)) {
            ownershipAccount(normalized)
                    .ifPresent(account ->
                            customerClient.assertReadable(
                                    account.getCustomerId(),
                                    accessToken,
                                    correlationId
                            )
                    );
        }

        return executor.execute(normalized);
    }

    private Optional<Account> ownershipAccount(
            BalanceCommandRequest request
    ) {
        return switch (request.type()) {
            case TRANSFER, WITHDRAWAL ->
                    find(request.sourceAccountId());

            case DEPOSIT ->
                    find(request.destinationAccountId());
        };
    }

    private Optional<Account> find(java.util.UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }

        return accountRepository.findById(accountId);
    }

    private BalanceCommandRequest normalize(
            BalanceCommandRequest request
    ) {
        return new BalanceCommandRequest(
                request.commandId(),
                request.type(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount().setScale(
                        2,
                        RoundingMode.UNNECESSARY
                ),
                request.currency()
        );
    }

    private boolean hasElevatedRole(
            Collection<? extends GrantedAuthority> authorities
    ) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ELEVATED_ROLES::contains);
    }

    private BalanceCommandResponse idempotencyConflict(
            BalanceCommandRequest request
    ) {
        return new BalanceCommandResponse(
                request.commandId(),
                request.type(),
                BalanceCommandStatus.REJECTED,
                BalanceFailureReason.IDEMPOTENCY_CONFLICT,
                null,
                null
        );
    }
}