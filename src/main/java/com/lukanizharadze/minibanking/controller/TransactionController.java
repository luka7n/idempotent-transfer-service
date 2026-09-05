package com.lukanizharadze.minibanking.controller;

import com.lukanizharadze.minibanking.dto.TransactionRequest;
import com.lukanizharadze.minibanking.dto.TransactionResponse;
import com.lukanizharadze.minibanking.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
