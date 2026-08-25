package com.asbank.customer.customer;

import com.asbank.customer.security.SecurityConfig;
import com.asbank.customer.web.ApiExceptionHandler;
import com.asbank.customer.web.CorrelationIdFilter;
import com.asbank.customer.web.SecurityProblemWriter;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import({
        SecurityConfig.class,
        CustomerService.class,
        SecurityProblemWriter.class,
        ApiExceptionHandler.class,
        CorrelationIdFilter.class
})
class SecurityIntegrationTest {

    private static final UUID CUSTOMER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String EXPECTED_CLIENT_ID = "as-bank-test";

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
    private CustomerRepository repository;

    @MockitoBean
    private CustomerMetrics metrics;

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
    void rejectsExpiredAccessToken() throws Exception {
        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "customer.read",
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
                "customer.read",
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
                "customer.read",
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
                "customer.read",
                List.of("CUSTOMER"),
                Instant.now().plusSeconds(300)
        );

        expectUnauthorized(token);
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        String validToken = validCustomerToken("customer-owner");

        expectUnauthorized(tamperSignature(validToken));
    }

    @Test
    void rejectsAuthenticatedCustomerWithoutRole() throws Exception {
        String token = token(
                ISSUER,
                "customer-owner",
                "access",
                EXPECTED_CLIENT_ID,
                "customer.read",
                List.of(),
                Instant.now().plusSeconds(300)
        );

        mockMvc.perform(
                        get(
                                "/api/v1/customers/{customerId}",
                                CUSTOMER_ID
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsCustomerReadingAnotherCustomersRecord()
            throws Exception {

        Customer customer = mock(Customer.class);

        when(customer.getSubject())
                .thenReturn("customer-owner");

        when(repository.findById(CUSTOMER_ID))
                .thenReturn(Optional.of(customer));

        String token = validCustomerToken("different-customer");

        mockMvc.perform(
                        get(
                                "/api/v1/customers/{customerId}",
                                CUSTOMER_ID
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isForbidden());
    }

    private void expectUnauthorized(String token) throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/customers/{customerId}",
                                CUSTOMER_ID
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    private String validCustomerToken(String subject) throws Exception {
        return token(
                ISSUER,
                subject,
                "access",
                EXPECTED_CLIENT_ID,
                "customer.read",
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
                .keyID("as-bank-security-test")
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