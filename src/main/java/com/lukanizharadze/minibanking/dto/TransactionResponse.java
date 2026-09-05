package com.lukanizharadze.minibanking.dto;

import com.lukanizharadze.minibanking.model.TransactionFailureReason;
import com.lukanizharadze.minibanking.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        TransactionStatus status,
        TransactionFailureReason failureReason,
        Instant createdAt
) {
}
