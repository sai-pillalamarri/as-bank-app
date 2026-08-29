package com.asbank.account.account;

import com.asbank.account.customer.CustomerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BalanceCommandServiceTest {

    private static final UUID COMMAND_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final UUID CUSTOMER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID SOURCE_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID DESTINATION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceCommandRepository commandRepository;

    @Mock
    private BalanceCommandExecutor executor;

    @Mock
    private CustomerClient customerClient;

    private BalanceCommandService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        service = new BalanceCommandService(
                accountRepository,
                commandRepository,
                executor,
                customerClient
        );
    }

    @Test
    void checksCustomerOwnershipAndNormalizesTransferAmount() {
        BalanceCommandRequest request = new BalanceCommandRequest(
                COMMAND_ID,
                BalanceCommandType.TRANSFER,
                SOURCE_ID,
                DESTINATION_ID,
                new BigDecimal("100.0"),
                "GBP"
        );

        Account source = account(SOURCE_ID);

        BalanceCommandResponse expected = new BalanceCommandResponse(
                COMMAND_ID,
                BalanceCommandType.TRANSFER,
                BalanceCommandStatus.APPLIED,
                null,
                new BigDecimal("900.00"),
                new BigDecimal("2600.00")
        );

        when(commandRepository.findById(COMMAND_ID))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(SOURCE_ID))
                .thenReturn(Optional.of(source));

        when(executor.execute(any(BalanceCommandRequest.class)))
                .thenReturn(expected);

        BalanceCommandResponse response = service.apply(
                request,
                "access-token",
                "correlation-id",
                customerAuthorities()
        );

        assertSame(expected, response);

        verify(customerClient).assertReadable(
                CUSTOMER_ID,
                "access-token",
                "correlation-id"
        );

        ArgumentCaptor<BalanceCommandRequest> captor =
                ArgumentCaptor.forClass(BalanceCommandRequest.class);

        verify(executor).execute(captor.capture());

        assertEquals(
                new BigDecimal("100.00"),
                captor.getValue().amount()
        );
    }

    @Test
    void checksDestinationOwnershipForDeposit() {
        BalanceCommandRequest request = new BalanceCommandRequest(
                COMMAND_ID,
                BalanceCommandType.DEPOSIT,
                null,
                DESTINATION_ID,
                new BigDecimal("50.00"),
                "GBP"
        );

        Account destination = account(DESTINATION_ID);

        when(commandRepository.findById(COMMAND_ID))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(DESTINATION_ID))
                .thenReturn(Optional.of(destination));

        when(executor.execute(any(BalanceCommandRequest.class)))
                .thenReturn(applied(request));

        service.apply(
                request,
                "access-token",
                "correlation-id",
                customerAuthorities()
        );

        verify(customerClient).assertReadable(
                CUSTOMER_ID,
                "access-token",
                "correlation-id"
        );

        verify(accountRepository).findById(DESTINATION_ID);
    }

    @Test
    void elevatedRoleSkipsCustomerOwnershipLookup() {
        BalanceCommandRequest request = transfer("100.00");

        when(commandRepository.findById(COMMAND_ID))
                .thenReturn(Optional.empty());

        when(executor.execute(any(BalanceCommandRequest.class)))
                .thenReturn(applied(request));

        service.apply(
                request,
                "access-token",
                "correlation-id",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_account.write")
                )
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(customerClient);
        verify(executor).execute(any(BalanceCommandRequest.class));
    }

    @Test
    void replaysMatchingExistingCommandWithoutCallingDownstream() {
        BalanceCommandRequest request = transfer("100.00");

        BalanceCommand existing = BalanceCommand.applied(
                request,
                new BigDecimal("900.00"),
                new BigDecimal("2600.00")
        );

        when(commandRepository.findById(COMMAND_ID))
                .thenReturn(Optional.of(existing));

        BalanceCommandResponse response = service.apply(
                request,
                "access-token",
                "correlation-id",
                customerAuthorities()
        );

        assertEquals(BalanceCommandStatus.APPLIED, response.status());
        assertEquals(
                new BigDecimal("900.00"),
                response.sourceBalanceAfter()
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(customerClient);
        verifyNoInteractions(executor);
    }

    @Test
    void rejectsChangedRequestUsingExistingCommandId() {
        BalanceCommandRequest original = transfer("100.00");

        BalanceCommand existing = BalanceCommand.applied(
                original,
                new BigDecimal("900.00"),
                new BigDecimal("2600.00")
        );

        BalanceCommandRequest changed = transfer("200.00");

        when(commandRepository.findById(COMMAND_ID))
                .thenReturn(Optional.of(existing));

        BalanceCommandResponse response = service.apply(
                changed,
                "access-token",
                "correlation-id",
                customerAuthorities()
        );

        assertEquals(
                BalanceCommandStatus.REJECTED,
                response.status()
        );

        assertEquals(
                BalanceFailureReason.IDEMPOTENCY_CONFLICT,
                response.failureReason()
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(customerClient);
        verifyNoInteractions(executor);
    }

    @Test
    void missingOwnershipAccountDoesNotCallCustomerService() {
        BalanceCommandRequest request = new BalanceCommandRequest(
                COMMAND_ID,
                BalanceCommandType.WITHDRAWAL,
                null,
                null,
                new BigDecimal("25.00"),
                "GBP"
        );

        when(commandRepository.findById(COMMAND_ID))
                .thenReturn(Optional.empty());

        when(executor.execute(any(BalanceCommandRequest.class)))
                .thenReturn(new BalanceCommandResponse(
                        COMMAND_ID,
                        BalanceCommandType.WITHDRAWAL,
                        BalanceCommandStatus.REJECTED,
                        BalanceFailureReason.INVALID_ACCOUNT_SELECTION,
                        null,
                        null
                ));

        BalanceCommandResponse response = service.apply(
                request,
                "access-token",
                "correlation-id",
                customerAuthorities()
        );

        assertEquals(
                BalanceFailureReason.INVALID_ACCOUNT_SELECTION,
                response.failureReason()
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(customerClient);
        verify(executor).execute(any(BalanceCommandRequest.class));
    }

    private BalanceCommandRequest transfer(String amount) {
        return new BalanceCommandRequest(
                COMMAND_ID,
                BalanceCommandType.TRANSFER,
                SOURCE_ID,
                DESTINATION_ID,
                new BigDecimal(amount),
                "GBP"
        );
    }

    private BalanceCommandResponse applied(
            BalanceCommandRequest request
    ) {
        return new BalanceCommandResponse(
                request.commandId(),
                request.type(),
                BalanceCommandStatus.APPLIED,
                null,
                new BigDecimal("900.00"),
                new BigDecimal("2600.00")
        );
    }

    private List<GrantedAuthority> customerAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                new SimpleGrantedAuthority("SCOPE_account.write")
        );
    }

    private Account account(UUID id) {
        return new Account(
                id,
                CUSTOMER_ID,
                "TEST-" + id.toString().substring(0, 20),
                AccountType.CURRENT,
                AccountStatus.ACTIVE,
                new BigDecimal("1000.00"),
                "GBP",
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}