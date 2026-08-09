package com.lukanizharadze.minibanking.controller;

import com.lukanizharadze.minibanking.dto.TransactionHistoryResponse;
import com.lukanizharadze.minibanking.service.TransactionService;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import com.lukanizharadze.minibanking.service.AccountService;
import org.springframework.http.HttpStatus;

import com.lukanizharadze.minibanking.dto.AccountResponse;
import jakarta.validation.Valid;
import com.lukanizharadze.minibanking.dto.OpenAccountRequest;

import java.time.Instant;
import java.util.List;

import com.lukanizharadze.minibanking.dto.UpdateAccountOwnerNameRequest;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse openAccount(@Valid @RequestBody OpenAccountRequest request) {
        return accountService.openAccount(request);
    }



    @GetMapping
    public List<AccountResponse> findAccounts() {
        return accountService.findAccounts();
    }

    @GetMapping("/{accountId}")
    public AccountResponse findAccount(@PathVariable Long accountId) {
        return accountService.findOneAccount(accountId);
    }

    @PatchMapping("/{accountId}")
    public AccountResponse updateOwnerName(@PathVariable Long accountId,
                                           @Valid @RequestBody UpdateAccountOwnerNameRequest request){
        return  accountService.updateOwnerName(accountId, request);
    }


    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeAccount(@PathVariable Long accountId) {
        accountService.closeAccount(accountId);
    }


    @GetMapping("/{accountId}/transactions")
    public TransactionHistoryResponse findAccountTransactions(
            @PathVariable Long accountId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return transactionService.findAccountHistory(
                accountId,
                from,
                to,
                page,
                size
        );
    }
}