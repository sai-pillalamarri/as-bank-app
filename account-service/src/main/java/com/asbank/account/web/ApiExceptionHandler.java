package com.asbank.account.web;

import com.asbank.account.account.AccountNotFoundException;
import com.asbank.account.customer.CustomerServiceUnavailableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleNotFound(
            AccountNotFoundException exception,
            HttpServletRequest request
    ) {
        return createProblem(
                HttpStatus.NOT_FOUND,
                "Account not found",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return createProblem(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "You are not allowed to access this account",
                request
        );
    }

    @ExceptionHandler({
            CustomerServiceUnavailableException.class,
            CallNotPermittedException.class,
            BulkheadFullException.class
    })
    ProblemDetail handleCustomerServiceUnavailable(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return createProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Customer service unavailable",
                "Account access cannot be verified right now",
                request
        );
    }

    private ProblemDetail createProblem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        status,
                        detail
                );

        problem.setTitle(title);
        problem.setProperty(
                "correlationId",
                request.getAttribute(
                        CorrelationIdFilter.ATTRIBUTE
                )
        );

        return problem;
    }
}