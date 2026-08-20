package com.asbank.customer.customer;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException() {
        super("Customer was not found");
    }
}