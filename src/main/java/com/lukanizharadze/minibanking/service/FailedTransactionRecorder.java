package com.lukanizharadze.minibanking.service;

import com.lukanizharadze.minibanking.dto.TransactionRequest;
import com.lukanizharadze.minibanking.entity.Transaction;
import com.lukanizharadze.minibanking.exception.IdempotencyException;
import com.lukanizharadze.minibanking.model.TransactionFailureReason;
import com.lukanizharadze.minibanking.repository.AccountRepository;
import com.lukanizharadze.minibanking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FailedTransactionRecorder {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(TransactionRequest request, String idempotencyKey, TransactionFailureReason failureReason) {
        try {
            transactionRepository.saveAndFlush(Transaction.failed(
                    accountRepository.getReferenceById(request.fromAccountId()),
                    accountRepository.getReferenceById(request.toAccountId()),
                    request.amount(),
                    idempotencyKey,
                    failureReason
            ));
        } catch (DataIntegrityViolationException ex) {
            throw new IdempotencyException(
                    "Idempotency key " + idempotencyKey + " is already in use",
                    ex
            );
        }
    }
}
