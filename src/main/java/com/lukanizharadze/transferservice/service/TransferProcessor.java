package com.lukanizharadze.transferservice.service;

import com.lukanizharadze.transferservice.dto.TransactionRequest;
import com.lukanizharadze.transferservice.dto.TransactionResponse;
import com.lukanizharadze.transferservice.entity.Account;
import com.lukanizharadze.transferservice.entity.Transaction;
import com.lukanizharadze.transferservice.exception.AccountNotFoundException;
import com.lukanizharadze.transferservice.exception.IdempotencyException;
import com.lukanizharadze.transferservice.exception.TransactionRejectedException;
import com.lukanizharadze.transferservice.mapper.TransactionMapper;
import com.lukanizharadze.transferservice.model.AccountStatus;
import com.lukanizharadze.transferservice.model.TransactionFailureReason;
import com.lukanizharadze.transferservice.repository.AccountRepository;
import com.lukanizharadze.transferservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransferProcessor {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionResponse process(TransactionRequest request, String idempotencyKey) {
        Account sourceAccount;
        Account destinationAccount;

        // Both rows are locked in ascending id order so that A -> B and B -> A cannot
        // each hold one row while waiting for the other.
        if (request.fromAccountId() < request.toAccountId()) {
            sourceAccount = lockAccount(request.fromAccountId());
            destinationAccount = lockAccount(request.toAccountId());
        } else {
            destinationAccount = lockAccount(request.toAccountId());
            sourceAccount = lockAccount(request.fromAccountId());
        }

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
                    "Account " + sourceAccount.getId() + " does not have enough funds"
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

    private Account lockAccount(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
