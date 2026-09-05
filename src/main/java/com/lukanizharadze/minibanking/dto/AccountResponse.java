package com.lukanizharadze.minibanking.dto;

import com.lukanizharadze.minibanking.model.AccountStatus;
import com.lukanizharadze.minibanking.model.Currency;

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
