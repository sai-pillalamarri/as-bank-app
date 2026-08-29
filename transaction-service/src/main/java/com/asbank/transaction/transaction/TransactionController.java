package com.asbank.transaction.transaction;

import com.asbank.transaction.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
public class TransactionController {

    private static final String IDEMPOTENCY_KEY =
            "Idempotency-Key";

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader(IDEMPOTENCY_KEY)
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9._-]{1,128}")
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(
                service.transfer(
                        idempotencyKey,
                        request,
                        authentication.getToken().getTokenValue(),
                        correlationId(servletRequest)
                )
        );
    }

    @PostMapping("/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @RequestHeader(IDEMPOTENCY_KEY)
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9._-]{1,128}")
            String idempotencyKey,
            @Valid @RequestBody DepositRequest request,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(
                service.deposit(
                        idempotencyKey,
                        request,
                        authentication.getToken().getTokenValue(),
                        correlationId(servletRequest)
                )
        );
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<TransactionResponse> withdrawal(
            @RequestHeader(IDEMPOTENCY_KEY)
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9._-]{1,128}")
            String idempotencyKey,
            @Valid @RequestBody WithdrawalRequest request,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(
                service.withdrawal(
                        idempotencyKey,
                        request,
                        authentication.getToken().getTokenValue(),
                        correlationId(servletRequest)
                )
        );
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<
            PagedResponse<LedgerEntryResponse>
            > history(
            @PathVariable UUID accountId,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt"
            )
            Pageable pageable,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(
                service.history(
                        accountId,
                        pageable,
                        authentication.getToken().getTokenValue(),
                        correlationId(servletRequest)
                )
        );
    }

    private String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(
                CorrelationIdFilter.ATTRIBUTE
        );
    }
}