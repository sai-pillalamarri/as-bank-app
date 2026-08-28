package com.asbank.account.account;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("The requested account does not exist");
    }
}