package com.lukanizharadze.minibanking.exception;

public class AccountNotEmptyException extends RuntimeException {
    public AccountNotEmptyException(Long accountId) {
        super("Account " + accountId + " cannot be closed because its balance is not zero");
    }
    

}
