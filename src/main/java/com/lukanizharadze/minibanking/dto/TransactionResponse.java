package com.lukanizharadze.minibanking.dto;

import java.math.BigDecimal;
import com.lukanizharadze.minibanking.model.TransactionStatus;
import com.lukanizharadze.minibanking.model.TransactionFailureReason;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        TransactionStatus status,
        TransactionFailureReason failureReason,
        Instant createdAt
)
{
}
