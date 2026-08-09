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

import org.springframework.web.bind.annotation.PatchMapping;
import com.lukanizharadze.minibanking.dto.UpdateAccountOwnerNameRequest;
import org.springframework.web.bind.annotation.DeleteMapping;

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


}