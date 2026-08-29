package com.asbank.transaction.account;

import com.asbank.transaction.transaction.BalanceCommandResult;
import com.asbank.transaction.web.CorrelationIdFilter;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    @Bulkhead(
            name = "accountService",
            type = Bulkhead.Type.SEMAPHORE
    )
    public BalanceCommandResult apply(
            BalanceCommandRequest request,
            String accessToken,
            String correlationId
    ) {
        try {
            return restClient.post()
                    .uri("/api/v1/internal/balance-commands")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + accessToken
                    )
                    .header(
                            CorrelationIdFilter.HEADER,
                            correlationId
                    )
                    .body(request)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::is4xxClientError,
                            (httpRequest, response) -> {
                                throw new AccessDeniedException(
                                        "Account command was denied"
                                );
                            }
                    )
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            (httpRequest, response) -> {
                                throw new AccountServiceUnavailableException(
                                        "Account service returned an error"
                                );
                            }
                    )
                    .body(BalanceCommandResult.class);
        } catch (ResourceAccessException exception) {
            throw new AccountServiceUnavailableException(
                    "Account service could not be reached",
                    exception
            );
        }
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    @Bulkhead(
            name = "accountService",
            type = Bulkhead.Type.SEMAPHORE
    )
    public void assertReadable(
            UUID accountId,
            String accessToken,
            String correlationId
    ) {
        try {
            restClient.get()
                    .uri(
                            "/api/v1/accounts/{accountId}",
                            accountId
                    )
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + accessToken
                    )
                    .header(
                            CorrelationIdFilter.HEADER,
                            correlationId
                    )
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                throw new AccessDeniedException(
                                        "Account access denied"
                                );
                            }
                    )
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            (request, response) -> {
                                throw new AccountServiceUnavailableException(
                                        "Account service returned an error"
                                );
                            }
                    )
                    .toBodilessEntity();
        } catch (ResourceAccessException exception) {
            throw new AccountServiceUnavailableException(
                    "Account service could not be reached",
                    exception
            );
        }
    }
}