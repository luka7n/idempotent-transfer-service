package com.lukanizharadze.minibanking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransactionRequest(
    @NotNull
    Long fromAccountId,

    @NotNull
    Long toAccountId,

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 18, fraction = 2)
    BigDecimal amount
        )
{

}
