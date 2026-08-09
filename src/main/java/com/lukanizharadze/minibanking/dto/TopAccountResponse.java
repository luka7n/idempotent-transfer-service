package com.lukanizharadze.minibanking.dto;

public record TopAccountResponse(
        Long accountId,
        String accountNumber,
        String ownerName,
        Long transactionCount
) {
}
