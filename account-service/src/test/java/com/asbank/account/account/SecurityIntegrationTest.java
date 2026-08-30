package com.asbank.account.account;

import com.asbank.account.customer.CustomerClient;
import com.asbank.account.security.SecurityConfig;
import com.asbank.account.web.ApiExceptionHandler;
import com.asbank.account.web.CorrelationIdFilter;
import com.asbank.account.web.SecurityProblemWriter;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import({
        SecurityConfig.class,
        AccountService.class,
        SecurityProblemWriter.class,
        ApiExceptionHandler.class,
        CorrelationIdFilter.class
})
class SecurityIntegrationTest {

    private static final UUID ACCOUNT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID CUSTOMER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String EXPECTED_CLIENT_ID = "as-bank-test";
    private static final String CORRELATION_ID = "account-security-test";

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

                exchange.sendResponseHeaders(200, body.length);
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
    private AccountRepository repository;

    @MockitoBean
    private CustomerClient customerClient;

    @MockitoBean
    private AccountMetrics metrics;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
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
    void allowsCustomerToReadOwnedAccount() throws Exception {
        Account account = account();

        when(repository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account));

        String token = validCustomerToken();

        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}",
                                ACCOUNT_ID
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
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                CorrelationIdFilter.HEADER,
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(ACCOUNT_ID.toString())
                )
                .andExpect(
                        jsonPath("$.customerId")
                                .value(CUSTOMER_ID.toString())
                )
                .andExpect(
                        jsonPath("$.accountNumber")
                                .value("10000001")
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("CURRENT")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                )
                .andExpect(
                        jsonPath("$.balance")
                                .value(1000.00)
                )
                .andExpect(
                        jsonPath("$.currency")
                                .value("GBP")
                );

        verify(customerClient).assertReadable(
                CUSTOMER_ID,
                token,
                CORRELATION_ID
        );
    }

    @Test
    void returnsPagedAccountsForAuthorizedCustomer() throws Exception {
        Account account = account();

        when(
                repository.findByCustomerId(
                        eq(CUSTOMER_ID),
                        any(Pageable.class)
                )
        ).thenReturn(
                new PageImpl<>(
                        List.of(account),
                        PageRequest.of(0, 20),
                        1
                )
        );

        String token = validCustomerToken();

        mockMvc.perform(
                        get(
                                "/api/v1/customers/{customerId}/accounts",
                                CUSTOMER_ID
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
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.items[0].id")
                                .value(ACCOUNT_ID.toString())
                )
                .andExpect(
                        jsonPath("$.page")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(20)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(1)
                );

        verify(customerClient).assertReadable(
                CUSTOMER_ID,
                token,
                CORRELATION_ID
        );
    }

    @Test
    void rejectsRequestWithoutAccessToken() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}",
                                ACCOUNT_ID
                        )
                                .header(
                                        CorrelationIdFilter.HEADER,
                                        CORRELATION_ID
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredAccessToken() throws Exception {
        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "account.read",
                List.of("CUSTOMER"),
                Instant.now().minusSeconds(60)
        );

        expectUnauthorized(token);
    }

    @Test
    void rejectsTokenFromWrongIssuer() throws Exception {
        String token = token(
                "https://wrong-issuer.example",
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "account.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );

        expectUnauthorized(token);
    }

    @Test
    void rejectsIdTokenAtApi() throws Exception {
        String token = token(
                ISSUER,
                "customer-owner",
                "id",
                EXPECTED_CLIENT_ID,
                "account.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );

        expectUnauthorized(token);
    }

    @Test
    void rejectsWrongClientId() throws Exception {
        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                "another-client",
                "account.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );

        expectUnauthorized(token);
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        expectUnauthorized(
                tamperSignature(validCustomerToken())
        );
    }

    @Test
    void rejectsAuthenticatedCustomerWithoutRole() throws Exception {
        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "account.read",
                List.of(),
                Instant.now().plusSeconds(300)
        );

        expectForbidden(token);
    }

    @Test
    void rejectsCustomerWithoutAccountReadScope() throws Exception {
        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "customer.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );

        expectForbidden(token);
    }

    @Test
    void rejectsCustomerWhenOwnershipCheckFails() throws Exception {
        when(repository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account()));

        String token = validCustomerToken();

        doThrow(
                new AccessDeniedException("Customer access denied")
        )
                .when(customerClient)
                .assertReadable(
                        CUSTOMER_ID,
                        token,
                        CORRELATION_ID
                );

        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}",
                                ACCOUNT_ID
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
    }

    @Test
    void allowsOperationsRoleWithoutCustomerOwnershipCall()
            throws Exception {

        when(repository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account()));

        String token = token(
                ISSUER,
                "operations-user",
                "access",
                EXPECTED_CLIENT_ID,
                "account.read",
                List.of("OPERATIONS"),
                Instant.now().plusSeconds(300)
        );

        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}",
                                ACCOUNT_ID
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
                .andExpect(status().isOk());

        verify(customerClient, never()).assertReadable(
                any(UUID.class),
                anyString(),
                anyString()
        );
    }

    private void expectUnauthorized(String token) throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}",
                                ACCOUNT_ID
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

    private void expectForbidden(String token) throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/accounts/{accountId}",
                                ACCOUNT_ID
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
    }

    private String validCustomerToken() throws Exception {
        return token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "https://api.aslearnings.online/account.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );
    }

    private Account account() {
        return new Account(
                ACCOUNT_ID,
                CUSTOMER_ID,
                "10000001",
                AccountType.CURRENT,
                AccountStatus.ACTIVE,
                new BigDecimal("1000.00"),
                "GBP",
                Instant.parse("2026-01-01T00:00:00Z")
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

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(expiresAt))
                .claim("token_use", tokenUse)
                .claim("client_id", clientId)
                .claim("scope", scope)
                .claim("cognito:groups", groups)
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(SIGNING_KEY.getKeyID())
                        .build(),
                claims
        );

        jwt.sign(new RSASSASigner(SIGNING_KEY));

        return jwt.serialize();
    }

    private static RSAKey createSigningKey() throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048);

        KeyPair pair = generator.generateKeyPair();

        return new RSAKey.Builder(
                (RSAPublicKey) pair.getPublic()
        )
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("as-bank-account-security-test")
                .build();
    }

    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.");

        char first = parts[2].charAt(0);
        char replacement = first == 'A' ? 'B' : 'A';

        parts[2] = replacement + parts[2].substring(1);

        return String.join(".", parts);
    }
}