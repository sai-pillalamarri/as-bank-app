package com.asbank.customer.security;

import com.asbank.customer.web.SecurityProblemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProblemWriter problemWriter,
            JwtAuthenticationConverter authenticationConverter
    ) throws Exception {

        // Bearer tokens are headers, not browser cookies, so CSRF does not protect this API.
        http.csrf(AbstractHttpConfigurer::disable);

        http
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/liveness",
                                "/actuator/health/readiness"
                        ).permitAll()
                        .requestMatchers("/actuator/prometheus")
                        .hasAuthority("SCOPE_metrics.read")
                        .anyRequest()
                        .authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                authenticationConverter
                        ))
                        .authenticationEntryPoint(
                                (request, response, exception) ->
                                        problemWriter.write(
                                                request,
                                                response,
                                                HttpStatus.UNAUTHORIZED,
                                                "Unauthorized",
                                                "A valid access token is required"
                                        )
                        )
                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        problemWriter.write(
                                                request,
                                                response,
                                                HttpStatus.FORBIDDEN,
                                                "Access denied",
                                                "The access token does not grant this operation"
                                        )
                        )
                );

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
            String issuerUri,

            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
            String jwkSetUri,

            @Value("${asbank.security.expected-client-id}")
            String expectedClientId
    ) {
        NimbusJwtDecoder decoder;

        if (StringUtils.hasText(jwkSetUri)) {
            decoder = NimbusJwtDecoder
                    .withJwkSetUri(jwkSetUri)
                    .build();
        } else {
            decoder = NimbusJwtDecoder
                    .withIssuerLocation(issuerUri)
                    .build();
        }

        OAuth2TokenValidator<Jwt> standardValidation =
                JwtValidators.createDefaultWithIssuer(issuerUri);

        OAuth2TokenValidator<Jwt> tokenUseValidation =
                requiredClaim("token_use", "access");

        OAuth2TokenValidator<Jwt> clientIdValidation =
                requiredClaim("client_id", expectedClientId);

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        standardValidation,
                        tokenUseValidation,
                        clientIdValidation
                )
        );

        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes =
                new JwtGrantedAuthoritiesConverter();

        scopes.setAuthoritiesClaimName("scope");
        scopes.setAuthorityPrefix("SCOPE_");

        Converter<Jwt, Collection<GrantedAuthority>> authorities =
                jwt -> {
                    Collection<GrantedAuthority> result =
                            new ArrayList<>(scopes.convert(jwt));

                    List<String> groups =
                            jwt.getClaimAsStringList("cognito:groups");

                    if (groups != null) {
                        groups.stream()
                                .map(group ->
                                        new SimpleGrantedAuthority(
                                                "ROLE_" + group
                                        )
                                )
                                .forEach(result::add);
                    }

                    return result;
                };

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        configuration.setAllowedMethods(
                List.of("GET")
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "X-Correlation-ID"
                )
        );

        configuration.setExposedHeaders(
                List.of("X-Correlation-ID")
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    private OAuth2TokenValidator<Jwt> requiredClaim(
            String claim,
            String expected
    ) {
        return jwt -> {
            String actual = jwt.getClaimAsString(claim);

            if (expected.equals(actual)) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error =
                    new OAuth2Error(
                            "invalid_token",
                            claim + " has an invalid value",
                            null
                    );

            return OAuth2TokenValidatorResult.failure(error);
        };
    }
}