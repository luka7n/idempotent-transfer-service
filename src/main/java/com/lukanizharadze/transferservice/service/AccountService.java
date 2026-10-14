package com.lukanizharadze.transferservice.service;

import com.lukanizharadze.transferservice.dto.AccountResponse;
import com.lukanizharadze.transferservice.dto.OpenAccountRequest;
import com.lukanizharadze.transferservice.dto.UpdateAccountOwnerNameRequest;
import com.lukanizharadze.transferservice.entity.Account;
import com.lukanizharadze.transferservice.exception.AccountNotEmptyException;
import com.lukanizharadze.transferservice.exception.AccountNotFoundException;
import com.lukanizharadze.transferservice.mapper.AccountMapper;
import com.lukanizharadze.transferservice.model.AccountStatus;
import com.lukanizharadze.transferservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String ACCOUNT_NUMBER_PREFIX = "GE";
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Transactional
    public AccountResponse openAccount(OpenAccountRequest request) {
        Account account = new Account(
                request.ownerName(),
                generateAccountNumber(),
                request.initialBalance(),
                request.currency()
        );

        accountRepository.save(account);
        return accountMapper.toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAccounts() {
        return accountRepository.findAll().stream()
                .map(accountMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findOneAccount(Long accountId) {
        return accountMapper.toResponse(getAccount(accountId));
    }

    @Transactional
    public AccountResponse updateOwnerName(Long accountId, UpdateAccountOwnerNameRequest request) {
        Account account = lockAccount(accountId);
        account.updateOwnerName(request.ownerName());
        return accountMapper.toResponse(account);
    }

    @Transactional
    public void closeAccount(Long accountId) {
        Account account = lockAccount(accountId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            return;
        }

        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new AccountNotEmptyException(accountId);
        }
        account.close();
    }

    private Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    // Hibernate writes every column when it updates an account. Without the same lock a transfer
    // takes, a transfer committed in between would have its balance overwritten by a stale one,
    // and closing could check a balance that is no longer current.
    private Account lockAccount(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private String generateAccountNumber() {
        String randomPart= UUID.randomUUID().toString().replace("-", "");
        return ACCOUNT_NUMBER_PREFIX + randomPart.substring(0, 18);
    }
}
