package com.lukanizharadze.transferservice.dto;

import java.util.List;

public record TransactionHistoryResponse(
        List<TransactionResponse> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
