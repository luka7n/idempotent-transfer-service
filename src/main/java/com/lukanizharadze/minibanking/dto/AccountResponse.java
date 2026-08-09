package com.lukanizharadze.minibanking.dto;

import java.math.BigDecimal;
import com.lukanizharadze.minibanking.model.Currency;
import com.lukanizharadze.minibanking.model.AccountStatus;
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
