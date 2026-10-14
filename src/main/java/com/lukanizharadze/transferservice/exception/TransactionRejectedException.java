package com.lukanizharadze.transferservice.exception;

import com.lukanizharadze.transferservice.model.TransactionFailureReason;
import lombok.Getter;

@Getter
public class TransactionRejectedException extends RuntimeException {

    private final TransactionFailureReason reason;

    public TransactionRejectedException(TransactionFailureReason reason, String message) {
        super(message);

        this.reason = reason;
    }
}
