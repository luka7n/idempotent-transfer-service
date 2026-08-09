package com.lukanizharadze.minibanking.exception;

public class SameAccountTransactionException extends RuntimeException {

    public SameAccountTransactionException(Long accountId) {
        super("Account " + accountId + " cannot transfer to itself");
    }
}