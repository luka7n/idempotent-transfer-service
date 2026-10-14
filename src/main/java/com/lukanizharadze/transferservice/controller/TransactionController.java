package com.lukanizharadze.transferservice.controller;

import com.lukanizharadze.transferservice.dto.TransactionRequest;
import com.lukanizharadze.transferservice.dto.TransactionResponse;
import com.lukanizharadze.transferservice.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "Transfer money")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                        @Valid @RequestBody TransactionRequest request) {

        return transactionService.transfer(request, idempotencyKey);
    }
}
