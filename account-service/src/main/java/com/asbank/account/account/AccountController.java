package com.asbank.account.account;

import com.asbank.account.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable UUID accountId,
            JwtAuthenticationToken authentication,
            HttpServletRequest request
    ) {
        AccountResponse response = accountService.getAccount(
                accountId,
                authentication.getToken().getTokenValue(),
                correlationId(request),
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<PagedResponse<AccountResponse>>
    getAccountsForCustomer(
            @PathVariable UUID customerId,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt"
            ) Pageable pageable,
            JwtAuthenticationToken authentication,
            HttpServletRequest request
    ) {
        PagedResponse<AccountResponse> response =
                accountService.getAccountsForCustomer(
                        customerId,
                        pageable,
                        authentication.getToken().getTokenValue(),
                        correlationId(request),
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