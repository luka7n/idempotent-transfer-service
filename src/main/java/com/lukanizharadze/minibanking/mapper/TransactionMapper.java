package com.lukanizharadze.minibanking.mapper;

import com.lukanizharadze.minibanking.dto.TransactionResponse;
import com.lukanizharadze.minibanking.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getFromAccount().getId(),
                transaction.getToAccount().getId(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getFailureReason(),
                transaction.getCreatedAt()
        );
    }
}
