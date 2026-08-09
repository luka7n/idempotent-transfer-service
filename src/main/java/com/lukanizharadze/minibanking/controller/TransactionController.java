package com.lukanizharadze.minibanking.controller;

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import com.lukanizharadze.minibanking.service.TransactionService;
import org.springframework.http.HttpStatus;
import com.lukanizharadze.minibanking.dto.TransactionResponse;
import jakarta.validation.Valid;
import com.lukanizharadze.minibanking.dto.TransactionRequest;




@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                        @Valid @RequestBody TransactionRequest request) {

        return transactionService.transfer(request, idempotencyKey);
    }
}