package com.asbank.transaction.account;

public class AccountServiceUnavailableException
        extends RuntimeException {

    public AccountServiceUnavailableException(
            String message
    ) {
        super(message);
    }

    public AccountServiceUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}