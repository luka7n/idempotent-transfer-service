package com.lukanizharadze.transferservice.dto;

import com.lukanizharadze.transferservice.model.TransactionFailureReason;
import com.lukanizharadze.transferservice.model.TransactionStatus;

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
