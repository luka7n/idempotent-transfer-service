package com.lukanizharadze.transferservice.dto;

import com.lukanizharadze.transferservice.model.Currency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record OpenAccountRequest(
        @NotBlank
        @Size(max = 50)
        String ownerName,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 18, fraction = 2)
        BigDecimal initialBalance,

        @NotNull
        Currency currency
) {
}
