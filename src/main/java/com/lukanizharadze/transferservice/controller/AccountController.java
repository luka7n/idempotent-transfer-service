package com.lukanizharadze.transferservice.controller;

import com.lukanizharadze.transferservice.dto.*;
import com.lukanizharadze.transferservice.service.AccountService;
import com.lukanizharadze.transferservice.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    @Operation(summary = "Open an account")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse openAccount(@Valid @RequestBody OpenAccountRequest request) {
        return accountService.openAccount(request);
    }

    @Operation(summary = "List all accounts")
    @GetMapping
    public List<AccountResponse> findAccounts() {
        return accountService.findAccounts();
    }

    @Operation(summary = "Get one account")
    @GetMapping("/{accountId}")
    public AccountResponse findAccount(@PathVariable Long accountId) {
        return accountService.findOneAccount(accountId);
    }

    @Operation(summary = "Update the owner name")
    @PatchMapping("/{accountId}")
    public AccountResponse updateOwnerName(@PathVariable Long accountId,
                                           @Valid @RequestBody UpdateAccountOwnerNameRequest request){
        return accountService.updateOwnerName(accountId, request);
    }

    @Operation(summary = "Close an account")
    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeAccount(@PathVariable Long accountId) {
        accountService.closeAccount(accountId);
    }

    @Operation(summary = "Get account history")
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

    @Operation(summary = "Get the top five accounts")
    @GetMapping("/top")
    public List<TopAccountResponse> findTopAccounts() {
        return transactionService.findTopAccounts();
    }
}
