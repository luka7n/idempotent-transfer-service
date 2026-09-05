package com.lukanizharadze.minibanking;

import com.lukanizharadze.minibanking.dto.TransactionRequest;
import com.lukanizharadze.minibanking.dto.TransactionResponse;
import com.lukanizharadze.minibanking.entity.Account;
import com.lukanizharadze.minibanking.entity.Transaction;
import com.lukanizharadze.minibanking.exception.TransactionRejectedException;
import com.lukanizharadze.minibanking.model.Currency;
import com.lukanizharadze.minibanking.model.TransactionFailureReason;
import com.lukanizharadze.minibanking.model.TransactionStatus;
import com.lukanizharadze.minibanking.repository.AccountRepository;
import com.lukanizharadze.minibanking.repository.TransactionRepository;
import com.lukanizharadze.minibanking.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransferConcurrencyIntegrationTest {

    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("80.00");
    private static final int TIMEOUT_SECONDS = 10;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void clearDatabase() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void concurrentTransfersFromTheSameAccountCannotOverdrawIt() throws Exception {
        Long sourceId = createAccount("Source Owner", "100.00", "TEST-SOURCE");
        Long firstDestinationId = createAccount("First Destination", "0.00", "TEST-DESTINATION-1");
        Long secondDestinationId = createAccount("Second Destination", "0.00", "TEST-DESTINATION-2");

        CyclicBarrier startBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        int successCount = 0;
        List<Throwable> failures = new ArrayList<>();
        try {
            Future<TransactionResponse> firstTransfer = executor.submit(
                    transferTask(startBarrier, sourceId, firstDestinationId, "concurrent-first"));
            Future<TransactionResponse> secondTransfer = executor.submit(
                    transferTask(startBarrier, sourceId, secondDestinationId, "concurrent-second"));

            for (Future<TransactionResponse> transfer : List.of(firstTransfer, secondTransfer)) {
                try {
                    transfer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    successCount++;
                } catch (ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(failures)
                .singleElement()
                .isInstanceOfSatisfying(TransactionRejectedException.class, rejected ->
                        assertThat(rejected.getReason()).isEqualTo(TransactionFailureReason.INSUFFICIENT_FUNDS));

        BigDecimal sourceBalance = balanceOf(sourceId);
        BigDecimal firstDestinationBalance = balanceOf(firstDestinationId);
        BigDecimal secondDestinationBalance = balanceOf(secondDestinationId);

        assertThat(sourceBalance).isEqualByComparingTo("20.00");
        assertThat(firstDestinationBalance.add(secondDestinationBalance)).isEqualByComparingTo("80.00");
        assertThat(sourceBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(firstDestinationBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(secondDestinationBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(sourceBalance.add(firstDestinationBalance).add(secondDestinationBalance))
                .isEqualByComparingTo("100.00");

        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    private Callable<TransactionResponse> transferTask(CyclicBarrier startBarrier,
                                                       Long sourceId,
                                                       Long destinationId,
                                                       String idempotencyKey) {
        return () -> {
            startBarrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return transactionService.transfer(
                    new TransactionRequest(sourceId, destinationId, TRANSFER_AMOUNT), idempotencyKey);
        };
    }

    private Long createAccount(String ownerName, String balance, String accountNumber) {
        Account account = new Account(ownerName, accountNumber, new BigDecimal(balance), Currency.GEL);
        return accountRepository.save(account).getId();
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }
}
