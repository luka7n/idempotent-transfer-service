package com.lukanizharadze.minibanking.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long accountId) {
        super("Account " + accountId + " was not found");
    }


}
