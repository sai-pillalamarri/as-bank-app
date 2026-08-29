package com.asbank.account.account;

import com.asbank.account.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/balance-commands")
public class BalanceCommandController {

    private final BalanceCommandService service;

    public BalanceCommandController(
            BalanceCommandService service
    ) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BalanceCommandResponse> apply(
            @Valid @RequestBody BalanceCommandRequest request,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest
    ) {
        BalanceCommandResponse response = service.apply(
                request,
                authentication.getToken().getTokenValue(),
                correlationId(servletRequest),
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(response);
    }

    private String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(
                CorrelationIdFilter.ATTRIBUTE
        );
    }
}