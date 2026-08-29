package com.asbank.account.account;

import com.asbank.account.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalanceCommandControllerTest {

    @Test
    void passesSecurityAndCorrelationContextToService() {
        BalanceCommandService service = mock(BalanceCommandService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);

        UUID commandId =
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        UUID sourceId =
                UUID.fromString("22222222-2222-2222-2222-222222222222");

        UUID destinationId =
                UUID.fromString("33333333-3333-3333-3333-333333333333");

        BalanceCommandRequest request = new BalanceCommandRequest(
                commandId,
                BalanceCommandType.TRANSFER,
                sourceId,
                destinationId,
                new BigDecimal("100.00"),
                "GBP"
        );

        BalanceCommandResponse expected = new BalanceCommandResponse(
                commandId,
                BalanceCommandType.TRANSFER,
                BalanceCommandStatus.APPLIED,
                null,
                new BigDecimal("900.00"),
                new BigDecimal("2600.00")
        );

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                new SimpleGrantedAuthority("SCOPE_account.write")
        );

        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .claim("sub", "customer")
                .build();

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt, authorities);

        when(servletRequest.getAttribute(CorrelationIdFilter.ATTRIBUTE))
                .thenReturn("test-correlation-id");

        when(service.apply(
                request,
                "access-token",
                "test-correlation-id",
                authorities
        )).thenReturn(expected);

        BalanceCommandController controller =
                new BalanceCommandController(service);

        ResponseEntity<BalanceCommandResponse> response =
                controller.apply(
                        request,
                        authentication,
                        servletRequest
                );

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());

        verify(service).apply(
                request,
                "access-token",
                "test-correlation-id",
                authorities
        );
    }
}