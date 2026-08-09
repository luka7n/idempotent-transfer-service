package com.lukanizharadze.minibanking.service;

import com.lukanizharadze.minibanking.dto.TopAccountResponse;
import com.lukanizharadze.minibanking.dto.TransactionHistoryResponse;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.lukanizharadze.minibanking.repository.AccountRepository;
import com.lukanizharadze.minibanking.repository.TransactionRepository;

import com.lukanizharadze.minibanking.mapper.TransactionMapper;
import org.springframework.transaction.annotation.Transactional;
import com.lukanizharadze.minibanking.dto.TransactionResponse;
import com.lukanizharadze.minibanking.dto.TransactionRequest;
import com.lukanizharadze.minibanking.exception.SameAccountTransactionException;
import com.lukanizharadze.minibanking.entity.Account;
import com.lukanizharadze.minibanking.entity.Transaction;
import com.lukanizharadze.minibanking.exception.AccountNotFoundException;
import com.lukanizharadze.minibanking.model.AccountStatus;
import com.lukanizharadze.minibanking.exception.TransactionRejectedException;
import com.lukanizharadze.minibanking.model.TransactionFailureReason;

import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import com.lukanizharadze.minibanking.exception.IdempotencyException;
import org.springframework.data.domain.PageRequest;



@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    private static final int MAX_PAGE_SIZE = 100;
    private static final int TOP_ACCOUNTS_LIMIT = 5;


    @Transactional
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

        Account sourceAccount = getAccount(request.fromAccountId());
        Account destinationAccount = getAccount(request.toAccountId());


        validateTransaction(
                sourceAccount,
                destinationAccount,
                request.amount()
        );


        sourceAccount.debit(request.amount());
        destinationAccount.credit(request.amount());

        Transaction transaction = saveTransaction(
                sourceAccount,
                destinationAccount,
                request.amount(),
                idempotencyKey
        );


        return transactionMapper.toResponse(transaction);

    }

    private void validateTransaction(Account sourceAccount,
                                        Account destinationAccount,
                                        BigDecimal amount) {

        if (sourceAccount.getStatus() == AccountStatus.CLOSED || destinationAccount.getStatus() == AccountStatus.CLOSED) {
            throw new TransactionRejectedException(
                    TransactionFailureReason.ACCOUNT_CLOSED,
                    "Transfer involves closed account"
            );


        }

        if (sourceAccount.getCurrency() != destinationAccount.getCurrency()) {
            throw new TransactionRejectedException(
                    TransactionFailureReason.CURRENCY_MISMATCH,
                    "Accounts have different currencies"
            );
        }

        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new TransactionRejectedException(
                    TransactionFailureReason.INSUFFICIENT_FUNDS,
                    "Account " + sourceAccount.getId() + " doesnot have enough funds"
            );
        }
    }

    private Transaction saveTransaction(Account sourceAccount, Account destinationAccount, BigDecimal amount,
                                        String idempotencyKey) {
        try {
            return transactionRepository.saveAndFlush(
                    new Transaction(sourceAccount, destinationAccount, amount, idempotencyKey)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new IdempotencyException(
                    "Idempotency key " + idempotencyKey + " is already in use",
                    ex
            );
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

        return transactionMapper.toResponse(stored);
    }

    private Account getAccount(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
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
