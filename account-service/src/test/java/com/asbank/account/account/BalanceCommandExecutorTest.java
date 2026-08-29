package com.asbank.account.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BalanceCommandExecutorTest {

    private static final UUID SOURCE_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID DESTINATION_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceCommandRepository commandRepository;

    private BalanceCommandExecutor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        executor = new BalanceCommandExecutor(
                accountRepository,
                commandRepository
        );
    }

    @Test
    void transfersMoneyBetweenAccounts() {
        BalanceCommandRequest request = transfer("100.00");

        Account source = account(
                SOURCE_ID,
                "1000.00"
        );

        Account destination = account(
                DESTINATION_ID,
                "2500.00"
        );

        when(commandRepository.findById(request.commandId()))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(SOURCE_ID))
                .thenReturn(Optional.of(source));

        when(accountRepository.findById(DESTINATION_ID))
                .thenReturn(Optional.of(destination));

        BalanceCommandResponse response =
                executor.execute(request);

        assertEquals(
                BalanceCommandStatus.APPLIED,
                response.status()
        );

        assertEquals(
                new BigDecimal("900.00"),
                source.getBalance()
        );

        assertEquals(
                new BigDecimal("2600.00"),
                destination.getBalance()
        );

        verify(commandRepository).save(any(BalanceCommand.class));
    }

    @Test
    void rejectsTransferWithInsufficientFunds() {
        BalanceCommandRequest request = transfer("1100.00");

        Account source = account(
                SOURCE_ID,
                "1000.00"
        );

        Account destination = account(
                DESTINATION_ID,
                "2500.00"
        );

        when(commandRepository.findById(request.commandId()))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(SOURCE_ID))
                .thenReturn(Optional.of(source));

        when(accountRepository.findById(DESTINATION_ID))
                .thenReturn(Optional.of(destination));

        BalanceCommandResponse response =
                executor.execute(request);

        assertEquals(
                BalanceCommandStatus.REJECTED,
                response.status()
        );

        assertEquals(
                BalanceFailureReason.INSUFFICIENT_FUNDS,
                response.failureReason()
        );

        assertEquals(
                new BigDecimal("1000.00"),
                source.getBalance()
        );
    }

    @Test
    void replaysPreviouslyAppliedCommand() {
        BalanceCommandRequest request = transfer("100.00");

        BalanceCommand existing = BalanceCommand.applied(
                request,
                new BigDecimal("900.00"),
                new BigDecimal("2600.00")
        );

        when(commandRepository.findById(request.commandId()))
                .thenReturn(Optional.of(existing));

        BalanceCommandResponse response =
                executor.execute(request);

        assertEquals(
                BalanceCommandStatus.APPLIED,
                response.status()
        );

        assertEquals(
                new BigDecimal("900.00"),
                response.sourceBalanceAfter()
        );

        verifyNoInteractions(accountRepository);
    }

    @Test
    void rejectsReusedCommandIdWithDifferentRequest() {
        BalanceCommandRequest original = transfer("100.00");

        BalanceCommand existing = BalanceCommand.applied(
                original,
                new BigDecimal("900.00"),
                new BigDecimal("2600.00")
        );

        BalanceCommandRequest changed =
                new BalanceCommandRequest(
                        original.commandId(),
                        BalanceCommandType.TRANSFER,
                        SOURCE_ID,
                        DESTINATION_ID,
                        new BigDecimal("200.00"),
                        "GBP"
                );

        when(commandRepository.findById(changed.commandId()))
                .thenReturn(Optional.of(existing));

        BalanceCommandResponse response =
                executor.execute(changed);

        assertEquals(
                BalanceCommandStatus.REJECTED,
                response.status()
        );

        assertEquals(
                BalanceFailureReason.IDEMPOTENCY_CONFLICT,
                response.failureReason()
        );

        verifyNoInteractions(accountRepository);
    }

    @Test
    void depositsMoney() {
        UUID commandId = UUID.randomUUID();

        BalanceCommandRequest request =
                new BalanceCommandRequest(
                        commandId,
                        BalanceCommandType.DEPOSIT,
                        null,
                        DESTINATION_ID,
                        new BigDecimal("50.00"),
                        "GBP"
                );

        Account destination = account(
                DESTINATION_ID,
                "2500.00"
        );

        when(commandRepository.findById(commandId))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(DESTINATION_ID))
                .thenReturn(Optional.of(destination));

        BalanceCommandResponse response =
                executor.execute(request);

        assertEquals(
                BalanceCommandStatus.APPLIED,
                response.status()
        );

        assertEquals(
                new BigDecimal("2550.00"),
                destination.getBalance()
        );
    }

    @Test
    void withdrawsMoney() {
        UUID commandId = UUID.randomUUID();

        BalanceCommandRequest request =
                new BalanceCommandRequest(
                        commandId,
                        BalanceCommandType.WITHDRAWAL,
                        SOURCE_ID,
                        null,
                        new BigDecimal("75.00"),
                        "GBP"
                );

        Account source = account(
                SOURCE_ID,
                "1000.00"
        );

        when(commandRepository.findById(commandId))
                .thenReturn(Optional.empty());

        when(accountRepository.findById(SOURCE_ID))
                .thenReturn(Optional.of(source));

        BalanceCommandResponse response =
                executor.execute(request);

        assertEquals(
                BalanceCommandStatus.APPLIED,
                response.status()
        );

        assertEquals(
                new BigDecimal("925.00"),
                source.getBalance()
        );
    }

    private BalanceCommandRequest transfer(String amount) {
        return new BalanceCommandRequest(
                UUID.randomUUID(),
                BalanceCommandType.TRANSFER,
                SOURCE_ID,
                DESTINATION_ID,
                new BigDecimal(amount),
                "GBP"
        );
    }

    private Account account(
            UUID id,
            String balance
    ) {
        return new Account(
                id,
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),
                "TEST-" + id.toString().substring(0, 20),
                AccountType.CURRENT,
                AccountStatus.ACTIVE,
                new BigDecimal(balance),
                "GBP",
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}