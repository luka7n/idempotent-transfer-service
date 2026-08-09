package com.lukanizharadze.minibanking.service;

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
import java.math.BigDecimal;




@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionResponse transfer(TransactionRequest request) {

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

        Transaction transaction = transactionRepository.save(new Transaction(sourceAccount, destinationAccount, request.amount()));

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

    private Account getAccount(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
