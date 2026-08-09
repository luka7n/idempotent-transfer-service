package com.lukanizharadze.minibanking.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;

import com.lukanizharadze.minibanking.service.AccountService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import com.lukanizharadze.minibanking.dto.AccountResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import com.lukanizharadze.minibanking.dto.OpenAccountRequest;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse openAccount(@Valid @RequestBody OpenAccountRequest request) {
        return accountService.openAccount(request);
    }



    @GetMapping
    public List<AccountResponse> findAccounts() {
        return accountService.findActiveAccounts();
    }

    @GetMapping("/{accountId}")
    public AccountResponse findAccount(@PathVariable Long accountId) {
        return accountService.findOneAccount(accountId);
    }


}