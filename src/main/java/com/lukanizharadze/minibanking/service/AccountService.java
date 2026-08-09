package com.lukanizharadze.minibanking.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.lukanizharadze.minibanking.repository.AccountRepository;
import com.lukanizharadze.minibanking.mapper.AccountMapper;
import org.springframework.transaction.annotation.Transactional;
import com.lukanizharadze.minibanking.dto.AccountResponse;
import com.lukanizharadze.minibanking.dto.OpenAccountRequest;
import com.lukanizharadze.minibanking.entity.Account;
import java.util.List;

import com.lukanizharadze.minibanking.model.AccountStatus;
import com.lukanizharadze.minibanking.exception.AccountNotFoundException;

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
    public List<AccountResponse> findActiveAccounts() {
        return accountRepository.findAccountsByStatus(AccountStatus.ACTIVE).stream()
                .map(accountMapper::toResponse)
                .toList();
    }


    @Transactional(readOnly = true)
    public AccountResponse findOneAccount(Long accountId) {
        return accountMapper.toResponse(getAccount(accountId));
    }


    private Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private String generateAccountNumber() {
        String randomPart= UUID.randomUUID().toString().replace("-", "");
        return ACCOUNT_NUMBER_PREFIX + randomPart.substring(0, 18);
    }
}
