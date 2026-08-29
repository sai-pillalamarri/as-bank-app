package com.asbank.transaction.transaction;

import com.asbank.transaction.account.AccountClient;
import com.asbank.transaction.account.AccountServiceUnavailableException;
import com.asbank.transaction.account.BalanceCommandRequest;
import com.asbank.transaction.security.SecurityConfig;
import com.asbank.transaction.web.ApiExceptionHandler;
import com.asbank.transaction.web.CorrelationIdFilter;
import com.asbank.transaction.web.SecurityProblemWriter;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import({
        SecurityConfig.class,
        TransactionService.class,
        SecurityProblemWriter.class,
        ApiExceptionHandler.class,
        CorrelationIdFilter.class
})
class SecurityIntegrationTest {

    private static final UUID SOURCE_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID DESTINATION_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final String EXPECTED_CLIENT_ID =
            "as-bank-test";

    private static final String CORRELATION_ID =
            "transaction-security-test";

    private static final RSAKey SIGNING_KEY;
    private static final HttpServer JWK_SERVER;
    private static final String ISSUER;

    static {
        try {
            SIGNING_KEY = createSigningKey();

            JWK_SERVER = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0),
                    0
            );

            JWK_SERVER.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(
                        SIGNING_KEY.toPublicJWK()
                )
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().add(
                        "Content-Type",
                        "application/json"
                );

                exchange.sendResponseHeaders(
                        200,
                        body.length
                );

                exchange.getResponseBody().write(body);
                exchange.close();
            });

            JWK_SERVER.start();

            ISSUER = "http://127.0.0.1:"
                    + JWK_SERVER.getAddress().getPort()
                    + "/issuer";

        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionStore store;

    @MockitoBean
    private LedgerEntryRepository ledgerRepository;

    @MockitoBean
    private AccountClient accountClient;

    @MockitoBean
    private TransactionMetrics metrics;

    @DynamicPropertySource
    static void securityProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> ISSUER
        );

        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:"
                        + JWK_SERVER.getAddress().getPort()
                        + "/jwks"
        );

        registry.add(
                "asbank.security.expected-client-id",
                () -> EXPECTED_CLIENT_ID
        );
    }

    @AfterAll
    static void stopJwkServer() {
        JWK_SERVER.stop(0);
    }

    @Test
    void allowsCustomerTransfer() throws Exception {
        String token = customerToken(
                "transaction.write"
        );

        TransactionRequest request =
                new TransactionRequest(
                        TransactionType.TRANSFER,
                        SOURCE_ID,
                        DESTINATION_ID,
                        new BigDecimal("100.00"),
                        "GBP"
                );

        BankTransaction transaction =
                stubPending(
                        "transfer-security-1",
                        request
                );

        stubApplied(
                transaction,
                token,
                CORRELATION_ID
        );

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "transfer-security-1"
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "sourceAccountId":
                                            "22222222-2222-2222-2222-222222222222",
                                          "destinationAccountId":
                                            "33333333-3333-3333-3333-333333333333",
                                          "amount": 100.00,
                                          "currency": "GBP"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                CorrelationIdFilter.HEADER,
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("APPLIED")
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("TRANSFER")
                );

        verify(accountClient).apply(
                any(BalanceCommandRequest.class),
                eq(token),
                eq(CORRELATION_ID)
        );
    }

    @Test
    void allowsCustomerDeposit() throws Exception {
        String token = customerToken(
                "transaction.write"
        );

        TransactionRequest request =
                new TransactionRequest(
                        TransactionType.DEPOSIT,
                        null,
                        DESTINATION_ID,
                        new BigDecimal("50.00"),
                        "GBP"
                );

        BankTransaction transaction =
                stubPending(
                        "deposit-security-1",
                        request
                );

        stubApplied(
                transaction,
                token,
                CORRELATION_ID
        );

        mockMvc.perform(
                        post("/api/v1/deposits")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "deposit-security-1"
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "destinationAccountId":
                                            "33333333-3333-3333-3333-333333333333",
                                          "amount": 50.00,
                                          "currency": "GBP"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.type")
                                .value("DEPOSIT")
                );
    }

    @Test
    void allowsCustomerWithdrawal() throws Exception {
        String token = customerToken(
                "transaction.write"
        );

        TransactionRequest request =
                new TransactionRequest(
                        TransactionType.WITHDRAWAL,
                        SOURCE_ID,
                        null,
                        new BigDecimal("25.00"),
                        "GBP"
                );

        BankTransaction transaction =
                stubPending(
                        "withdrawal-security-1",
                        request
                );

        stubApplied(
                transaction,
                token,
                CORRELATION_ID
        );

        mockMvc.perform(
                        post("/api/v1/withdrawals")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "withdrawal-security-1"
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "sourceAccountId":
                                            "22222222-2222-2222-2222-222222222222",
                                          "amount": 25.00,
                                          "currency": "GBP"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.type")
                                .value("WITHDRAWAL")
                );
    }

    @Test
    void allowsCustomerToReadOwnedAccountHistory()
            throws Exception {

        String token = customerToken(
                "transaction.read"
        );

        PageRequest pageable =
                PageRequest.of(0, 20);

        when(
                ledgerRepository.findByAccountId(
                        eq(SOURCE_ID),
                        any()
                )
        ).thenReturn(
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}/transactions",
                                SOURCE_ID
                        )
                                .param("page", "0")
                                .param("size", "20")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(0)
                );

        verify(accountClient).assertReadable(
                SOURCE_ID,
                token,
                CORRELATION_ID
        );
    }

    @Test
    void rejectsRequestWithoutAccessToken()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}/transactions",
                                SOURCE_ID
                        )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.title")
                                .value("Unauthorized")
                );
    }

    @Test
    void rejectsExpiredAccessToken()
            throws Exception {

        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "transaction.read",
                List.of("CUSTOMER"),
                Instant.now().minusSeconds(60)
        );

        expectUnauthorized(token);
    }

    @Test
    void rejectsWrongIssuer()
            throws Exception {

        String token = token(
                "https://wrong-issuer.example",
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "transaction.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );

        expectUnauthorized(token);
    }

    @Test
    void rejectsIdToken()
            throws Exception {

        String token = token(
                ISSUER,
                "customer-owner",
                "id",
                EXPECTED_CLIENT_ID,
                "transaction.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );

        expectUnauthorized(token);
    }

    @Test
    void rejectsWrongClientId()
            throws Exception {

        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                "another-client",
                "transaction.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );

        expectUnauthorized(token);
    }

    @Test
    void rejectsTamperedSignature()
            throws Exception {

        expectUnauthorized(
                tamperSignature(
                        customerToken(
                                "transaction.read"
                        )
                )
        );
    }

    @Test
    void rejectsAuthenticatedUserWithoutRole()
            throws Exception {

        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "transaction.read",
                List.of(),
                Instant.now().plusSeconds(300)
        );

        expectForbidden(token);
    }

    @Test
    void rejectsCustomerWithoutTransactionReadScope()
            throws Exception {

        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "account.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );

        expectForbidden(token);
    }

    @Test
    void rejectsTransferWithoutTransactionWriteScope()
            throws Exception {

        String token = customerToken(
                "transaction.read"
        );

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "transfer-scope-test"
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "sourceAccountId":
                                            "22222222-2222-2222-2222-222222222222",
                                          "destinationAccountId":
                                            "33333333-3333-3333-3333-333333333333",
                                          "amount": 100.00,
                                          "currency": "GBP"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(accountClient);
    }

    @Test
    void returnsConflictWhenIdempotencyKeyIsReused()
            throws Exception {

        String token = customerToken(
                "transaction.write"
        );

        BankTransaction existing =
                BankTransaction.pending(
                        "duplicate-key",
                        new TransactionRequest(
                                TransactionType.TRANSFER,
                                SOURCE_ID,
                                DESTINATION_ID,
                                new BigDecimal("100.00"),
                                "GBP"
                        )
                );

        when(
                store.findByIdempotencyKey(
                        "duplicate-key"
                )
        ).thenReturn(Optional.of(existing));

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "duplicate-key"
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "sourceAccountId":
                                            "22222222-2222-2222-2222-222222222222",
                                          "destinationAccountId":
                                            "33333333-3333-3333-3333-333333333333",
                                          "amount": 200.00,
                                          "currency": "GBP"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.title")
                                .value("Idempotency conflict")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                );

        verifyNoInteractions(accountClient);
    }

    @Test
    void mapsAccountServiceFailureTo503()
            throws Exception {

        String token = customerToken(
                "transaction.write"
        );

        TransactionRequest request =
                new TransactionRequest(
                        TransactionType.TRANSFER,
                        SOURCE_ID,
                        DESTINATION_ID,
                        new BigDecimal("100.00"),
                        "GBP"
                );

        BankTransaction transaction =
                stubPending(
                        "unavailable-test",
                        request
                );

        when(
                accountClient.apply(
                        any(BalanceCommandRequest.class),
                        eq(token),
                        eq(CORRELATION_ID)
                )
        ).thenThrow(
                new AccountServiceUnavailableException(
                        "Downstream failure"
                )
        );

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "unavailable-test"
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "sourceAccountId":
                                            "22222222-2222-2222-2222-222222222222",
                                          "destinationAccountId":
                                            "33333333-3333-3333-3333-333333333333",
                                          "amount": 100.00,
                                          "currency": "GBP"
                                        }
                                        """)
                )
                .andExpect(
                        status().isServiceUnavailable()
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Service unavailable")
                );
    }

    @Test
    void rejectsInvalidTransactionRequest()
            throws Exception {

        String token = customerToken(
                "transaction.write"
        );

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "validation-test"
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "sourceAccountId":
                                            "22222222-2222-2222-2222-222222222222",
                                          "destinationAccountId":
                                            "33333333-3333-3333-3333-333333333333",
                                          "amount": 0.00,
                                          "currency": "GBP"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.title")
                                .value("Invalid request")
                );

        verifyNoInteractions(accountClient);
    }

    @Test
    void allowsBrowserPreflightForTransfer()
            throws Exception {

        mockMvc.perform(
                        options("/api/v1/transfers")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "http://localhost:5173"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "authorization,"
                                                + "content-type,"
                                                + "idempotency-key,"
                                                + "x-correlation-id"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                "http://localhost:5173"
                        )
                );
    }

    private BankTransaction stubPending(
            String idempotencyKey,
            TransactionRequest request
    ) {
        when(
                store.findByIdempotencyKey(
                        idempotencyKey
                )
        ).thenReturn(Optional.empty());

        BankTransaction transaction =
                BankTransaction.pending(
                        idempotencyKey,
                        request
                );

        when(
                store.createPending(
                        eq(idempotencyKey),
                        any(TransactionRequest.class)
                )
        ).thenReturn(transaction);

        return transaction;
    }

    private void stubApplied(
            BankTransaction transaction,
            String token,
            String correlationId
    ) {
        when(
                accountClient.apply(
                        any(BalanceCommandRequest.class),
                        eq(token),
                        eq(correlationId)
                )
        ).thenReturn(
                new BalanceCommandResult(
                        "APPLIED",
                        null
                )
        );

        TransactionResponse response =
                new TransactionResponse(
                        transaction.getId(),
                        transaction.getType(),
                        TransactionStatus.APPLIED,
                        null,
                        transaction.getSourceAccountId(),
                        transaction.getDestinationAccountId(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        transaction.getCreatedAt(),
                        Instant.now()
                );

        when(
                store.complete(
                        eq(transaction.getId()),
                        any(BalanceCommandResult.class)
                )
        ).thenReturn(response);
    }

    private void expectUnauthorized(String token)
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}/transactions",
                                SOURCE_ID
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    private void expectForbidden(String token)
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}/transactions",
                                SOURCE_ID
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                )
                .andExpect(status().isForbidden());

        verify(
                accountClient,
                never()
        ).assertReadable(
                any(UUID.class),
                any(),
                any()
        );
    }

    private String customerToken(String scope)
            throws Exception {

        return token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                scope,
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );
    }

    private static String token(
            String issuer,
            String subject,
            String tokenUse,
            String clientId,
            String scope,
            List<String> groups,
            Instant expiresAt
    ) throws Exception {

        Instant now = Instant.now();

        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject(subject)
                        .issueTime(
                                Date.from(
                                        now.minusSeconds(5)
                                )
                        )
                        .expirationTime(
                                Date.from(expiresAt)
                        )
                        .claim(
                                "token_use",
                                tokenUse
                        )
                        .claim(
                                "client_id",
                                clientId
                        )
                        .claim(
                                "scope",
                                scope
                        )
                        .claim(
                                "cognito:groups",
                                groups
                        )
                        .build();

        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(
                                JWSAlgorithm.RS256
                        )
                                .type(
                                        JOSEObjectType.JWT
                                )
                                .keyID(
                                        SIGNING_KEY.getKeyID()
                                )
                                .build(),
                        claims
                );

        jwt.sign(
                new RSASSASigner(
                        SIGNING_KEY
                )
        );

        return jwt.serialize();
    }

    private static RSAKey createSigningKey()
            throws Exception {

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048);

        KeyPair pair =
                generator.generateKeyPair();

        return new RSAKey.Builder(
                (RSAPublicKey) pair.getPublic()
        )
                .privateKey(
                        (RSAPrivateKey) pair.getPrivate()
                )
                .keyID(
                        "as-bank-transaction-security-test"
                )
                .build();
    }

    private static String tamperSignature(
            String token
    ) {
        String[] parts = token.split("\\.");

        char first =
                parts[2].charAt(0);

        char replacement =
                first == 'A'
                        ? 'B'
                        : 'A';

        parts[2] =
                replacement
                        + parts[2].substring(1);

        return String.join(".", parts);
    }
}