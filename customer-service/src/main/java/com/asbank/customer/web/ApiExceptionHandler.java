package com.asbank.customer.web;

import com.asbank.customer.customer.CustomerNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail handleNotFound(
            CustomerNotFoundException exception,
            HttpServletRequest request
    ) {
        return createProblem(
                HttpStatus.NOT_FOUND,
                "Customer not found",
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
                "You are not allowed to access this customer",
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
                ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(title);
        problem.setProperty(
                "correlationId",
                request.getAttribute(CorrelationIdFilter.ATTRIBUTE)
        );

        return problem;
    }
}