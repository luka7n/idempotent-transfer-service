package com.lukanizharadze.minibanking.exception;

import com.lukanizharadze.minibanking.model.TransactionFailureReason;
import lombok.Getter;

@Getter
public class TransactionRejectedException extends RuntimeException {

    private final TransactionFailureReason reason;

    public TransactionRejectedException(TransactionFailureReason reason, String message) {
        super(message);

        this.reason = reason;
    }
}
