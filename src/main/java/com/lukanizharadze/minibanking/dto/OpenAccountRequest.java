package com.lukanizharadze.minibanking.dto;

import com.lukanizharadze.minibanking.model.Currency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record OpenAccountRequest(
        @NotBlank
        @Size(max = 50)
        String ownerName,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal initialBalance,

        @NotNull
        Currency currency
) {
}
