package com.lukanizharadze.transferservice.controller;

import com.lukanizharadze.transferservice.dto.TransactionRequest;
import com.lukanizharadze.transferservice.dto.TransactionResponse;
import com.lukanizharadze.transferservice.service.TransactionService;
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
