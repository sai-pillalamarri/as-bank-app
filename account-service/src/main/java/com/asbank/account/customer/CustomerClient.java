package com.asbank.account.customer;

import com.asbank.account.web.CorrelationIdFilter;
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
public class CustomerClient {

    private final RestClient restClient;

    public CustomerClient(RestClient customerServiceRestClient) {
        this.restClient = customerServiceRestClient;
    }

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService")
    @Bulkhead(
            name = "customerService",
            type = Bulkhead.Type.SEMAPHORE
    )
    public void assertReadable(
            UUID customerId,
            String accessToken,
            String correlationId
    ) {
        try {
            restClient.get()
                    .uri(
                            "/api/v1/customers/{customerId}",
                            customerId
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
                                        "Customer access denied"
                                );
                            }
                    )
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            (request, response) -> {
                                throw new CustomerServiceUnavailableException(
                                        "Customer service returned an error"
                                );
                            }
                    )
                    .toBodilessEntity();
        } catch (ResourceAccessException exception) {
            throw new CustomerServiceUnavailableException(
                    "Customer service could not be reached",
                    exception
            );
        }
    }
}