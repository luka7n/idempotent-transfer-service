package com.lukanizharadze.transferservice.service;

import com.lukanizharadze.transferservice.dto.TopAccountResponse;
import com.lukanizharadze.transferservice.dto.TransactionHistoryResponse;
import com.lukanizharadze.transferservice.dto.TransactionRequest;
import com.lukanizharadze.transferservice.dto.TransactionResponse;
import com.lukanizharadze.transferservice.entity.Transaction;
import com.lukanizharadze.transferservice.exception.AccountNotFoundException;
import com.lukanizharadze.transferservice.exception.IdempotencyException;
import com.lukanizharadze.transferservice.exception.SameAccountTransactionException;
import com.lukanizharadze.transferservice.exception.TransactionRejectedException;
import com.lukanizharadze.transferservice.mapper.TransactionMapper;
import com.lukanizharadze.transferservice.model.TransactionStatus;
import com.lukanizharadze.transferservice.repository.AccountRepository;
import com.lukanizharadze.transferservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final TransferProcessor transferProcessor;
    private final FailedTransactionRecorder failedTransactionRecorder;

    private static final int MAX_PAGE_SIZE = 100;
    private static final int TOP_ACCOUNTS_LIMIT = 5;

    public TransactionResponse transfer(TransactionRequest request, String idempotencyKey) {

        Optional<Transaction> stored = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (stored.isPresent()) {
            return handleExistingTransaction(
                    stored.get(),
                    request,
                    idempotencyKey
            );
        }

        if (request.fromAccountId().equals(request.toAccountId())) {

            throw new SameAccountTransactionException(request.fromAccountId());
        }

        try {
            return transferProcessor.process(request, idempotencyKey);
        } catch (TransactionRejectedException ex) {
            // transfer() is deliberately not transactional: the processor transaction has already
            // rolled back and released its account locks by the time we get here, so the recorder
            // can open its own transaction without waiting on rows the caller still holds.
            failedTransactionRecorder.record(request, idempotencyKey, ex.getReason());
            throw ex;
        }
    }

    private TransactionResponse handleExistingTransaction(Transaction stored,
                                                        TransactionRequest request,
                                                        String idempotencyKey) {
        boolean sameParameters =
                stored.getFromAccount().getId().equals(request.fromAccountId())
                        && stored.getToAccount().getId().equals(request.toAccountId())
                        && stored.getAmount().compareTo(request.amount()) == 0;

        if (!sameParameters) {
            throw new IdempotencyException(
                    "Idempotency key " + idempotencyKey
                            + " was already used with different parameters"
            );
        }

        if (stored.getStatus() == TransactionStatus.FAILED) {
            throw new TransactionRejectedException(
                    stored.getFailureReason(),
                    "Transfer was already rejected with reason " + stored.getFailureReason()
            );
        }

        return transactionMapper.toResponse(stored);
    }

    @Transactional(readOnly = true)
    public TransactionHistoryResponse findAccountHistory(Long accountId, Instant fromDate, Instant toDate,
                                                         int page, int size) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<TransactionResponse> history = transactionRepository
                .findHistory(
                        accountId,
                        fromDate,
                        toDate,
                        PageRequest.of(pageNumber, pageSize)
                )
                .map(transactionMapper::toResponse);

        return new TransactionHistoryResponse(
                history.getContent(),
                history.getNumber(),
                history.getSize(),
                history.getTotalElements(),
                history.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<TopAccountResponse> findTopAccounts() {
        return transactionRepository.findTopAccountsByTransactionCount(
                PageRequest.of(0, TOP_ACCOUNTS_LIMIT)
        );
    }
}
