package com.lukanizharadze.transferservice.dto;

public record TopAccountResponse(
        Long accountId,
        String accountNumber,
        String ownerName,
        Long transactionCount
) {
}
