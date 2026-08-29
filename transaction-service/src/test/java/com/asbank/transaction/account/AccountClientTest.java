package com.asbank.transaction.account;

import com.asbank.transaction.transaction.BalanceCommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountClientTest {

    private MockRestServiceServer server;
    private AccountClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl("http://account-service");

        server = MockRestServiceServer
                .bindTo(builder)
                .build();

        client = new AccountClient(builder.build());
    }

    @Test
    void appliesIdempotentBalanceCommand() {
        UUID commandId = UUID.randomUUID();

        server.expect(
                        requestTo(
                                "http://account-service" +
                                        "/api/v1/internal/balance-commands"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer token"
                        )
                )
                .andExpect(
                        header(
                                "X-Correlation-ID",
                                "correlation-1"
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "status": "APPLIED",
                                  "failureReason": null
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        BalanceCommandResult result = client.apply(
                new BalanceCommandRequest(
                        commandId,
                        "TRANSFER",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("100.00"),
                        "GBP"
                ),
                "token",
                "correlation-1"
        );

        assertTrue(result.applied());

        server.verify();
    }

    @Test
    void verifiesReadableAccount() {
        UUID accountId = UUID.randomUUID();

        server.expect(
                        requestTo(
                                "http://account-service/api/v1/accounts/"
                                        + accountId
                        )
                )
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        client.assertReadable(
                accountId,
                "token",
                "correlation-2"
        );

        server.verify();
    }

    @Test
    void mapsAccountServiceFailureToUnavailable() {
        server.expect(
                        requestTo(
                                "http://account-service" +
                                        "/api/v1/internal/balance-commands"
                        )
                )
                .andRespond(withServerError());

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> client.apply(
                        new BalanceCommandRequest(
                                UUID.randomUUID(),
                                "DEPOSIT",
                                null,
                                UUID.randomUUID(),
                                new BigDecimal("50.00"),
                                "GBP"
                        ),
                        "token",
                        "correlation-3"
                )
        );

        server.verify();
    }
}