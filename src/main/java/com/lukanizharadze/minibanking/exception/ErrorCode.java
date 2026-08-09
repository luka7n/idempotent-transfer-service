package com.lukanizharadze.minibanking.exception;

public enum ErrorCode {
    VALIDATION_FAILED,
    INVALID_REQUEST,
    ACCOUNT_NOT_FOUND,
    ACCOUNT_NOT_EMPTY,
    SAME_ACCOUNT_TRANSACTION,
    IDEMPOTENCY_KEY_CONFLICT,
    IDEMPOTENCY_KEY_REQUIRED
}

