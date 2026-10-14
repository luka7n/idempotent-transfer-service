package com.lukanizharadze.transferservice.dto;

import com.lukanizharadze.transferservice.model.AccountStatus;
import com.lukanizharadze.transferservice.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String ownerName,
        String accountNumber,
        BigDecimal balance,
        Currency currency,
        AccountStatus status,
        Instant createdAt
) {
}
