package com.lukanizharadze.transferservice;

import com.lukanizharadze.transferservice.dto.TransactionRequest;
import com.lukanizharadze.transferservice.dto.TransactionResponse;
import com.lukanizharadze.transferservice.dto.UpdateAccountOwnerNameRequest;
import com.lukanizharadze.transferservice.entity.Account;
import com.lukanizharadze.transferservice.entity.Transaction;
import com.lukanizharadze.transferservice.exception.TransactionRejectedException;
import com.lukanizharadze.transferservice.model.Currency;
import com.lukanizharadze.transferservice.model.TransactionFailureReason;
import com.lukanizharadze.transferservice.model.TransactionStatus;
import com.lukanizharadze.transferservice.repository.AccountRepository;
import com.lukanizharadze.transferservice.repository.TransactionRepository;
import com.lukanizharadze.transferservice.service.AccountService;
import com.lukanizharadze.transferservice.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferConcurrencyIntegrationTest {

    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("80.00");
    private static final int TIMEOUT_SECONDS = 10;
    private static final int THREAD_COUNT = 20;
    private static final int RANDOM_TRANSFER_COUNT = 200;
    private static final long LOCK_WAIT_MILLIS = 1000;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private ExecutorService executor;

    @BeforeEach
    void clearDatabase() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        executor = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    @Test
    void concurrentTransfersFromTheSameAccountCannotOverdrawIt() throws Exception {
        Long sourceId = createAccount("Source Owner", "100.00", "TEST-SOURCE");
        Long firstDestinationId = createAccount("First Destination", "0.00", "TEST-DESTINATION-1");
        Long secondDestinationId = createAccount("Second Destination", "0.00", "TEST-DESTINATION-2");

        List<Future<TransactionResponse>> transfers = startTogether(List.of(
                transferTask(sourceId, firstDestinationId, TRANSFER_AMOUNT, "concurrent-first"),
                transferTask(sourceId, secondDestinationId, TRANSFER_AMOUNT, "concurrent-second")));

        int successCount = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<TransactionResponse> transfer : transfers) {
            try {
                transfer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException ex) {
                failures.add(ex.getCause());
            }
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
        assertThat(transactions).hasSize(2);

        assertThat(transactions)
                .filteredOn(transaction -> transaction.getStatus() == TransactionStatus.SUCCESS)
                .singleElement()
                .satisfies(success -> assertThat(success.getFailureReason()).isNull());

        assertThat(transactions)
                .filteredOn(transaction -> transaction.getStatus() == TransactionStatus.FAILED)
                .singleElement()
                .satisfies(failed -> assertThat(failed.getFailureReason())
                        .isEqualTo(TransactionFailureReason.INSUFFICIENT_FUNDS));
    }

    @Test
    void concurrentRetriesWithTheSameKeyMoveMoneyOnceAndReturnTheSameTransaction() throws Exception {
        Long sourceId = createAccount("Source Owner", "100.00", "TEST-SOURCE");
        Long destinationId = createAccount("Destination Owner", "0.00", "TEST-DESTINATION");

        List<Future<TransactionResponse>> retries = startTogether(Collections.nCopies(THREAD_COUNT,
                transferTask(sourceId, destinationId, TRANSFER_AMOUNT, "concurrent-retry")));

        Set<Long> transactionIds = new HashSet<>();
        for (Future<TransactionResponse> retry : retries) {
            transactionIds.add(retry.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).id());
        }

        assertThat(transactionIds).hasSize(1);
        assertThat(balanceOf(sourceId)).isEqualByComparingTo("20.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("80.00");
        assertThat(transactionRepository.findAll())
                .singleElement()
                .satisfies(success -> assertThat(success.getStatus()).isEqualTo(TransactionStatus.SUCCESS));
    }

    @Test
    void concurrentRetriesOfARejectedTransferAllReturnTheSameRejection() throws Exception {
        Long sourceId = createAccount("Source Owner", "50.00", "TEST-SOURCE");
        Long destinationId = createAccount("Destination Owner", "0.00", "TEST-DESTINATION");

        List<Future<TransactionResponse>> retries = startTogether(Collections.nCopies(THREAD_COUNT,
                transferTask(sourceId, destinationId, TRANSFER_AMOUNT, "concurrent-rejected-retry")));

        for (Future<TransactionResponse> retry : retries) {
            assertThatThrownBy(() -> retry.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(TransactionRejectedException.class, rejected ->
                            assertThat(rejected.getReason()).isEqualTo(TransactionFailureReason.INSUFFICIENT_FUNDS));
        }

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("50.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("0.00");
        assertThat(transactionRepository.findAll())
                .singleElement()
                .satisfies(failed -> assertThat(failed.getStatus()).isEqualTo(TransactionStatus.FAILED));
    }

    @Test
    void renamingAnAccountDuringATransferDoesNotUndoTheTransfer() throws Exception {
        Long sourceId = createAccount("Source Owner", "100.00", "TEST-SOURCE");
        Long destinationId = createAccount("Destination Owner", "0.00", "TEST-DESTINATION");

        Future<TransactionResponse> transfer = transactionTemplate.execute(status -> {
            accountService.updateOwnerName(sourceId, new UpdateAccountOwnerNameRequest("Renamed Owner"));
            Future<TransactionResponse> concurrentTransfer = executor.submit(
                    transferTask(sourceId, destinationId, TRANSFER_AMOUNT, "rename-race"));
            awaitBeforeCommit(concurrentTransfer);
            return concurrentTransfer;
        });

        assertThat(transfer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(balanceOf(sourceId)).isEqualByComparingTo("20.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("80.00");
        assertThat(accountRepository.findById(sourceId).orElseThrow().getOwnerName()).isEqualTo("Renamed Owner");
    }

    @Test
    void closingAnAccountDuringATransferIntoItRejectsTheTransfer() throws Exception {
        Long sourceId = createAccount("Source Owner", "100.00", "TEST-SOURCE");
        Long destinationId = createAccount("Destination Owner", "0.00", "TEST-DESTINATION");

        Future<TransactionResponse> transfer = transactionTemplate.execute(status -> {
            accountService.closeAccount(destinationId);
            Future<TransactionResponse> concurrentTransfer = executor.submit(
                    transferTask(sourceId, destinationId, TRANSFER_AMOUNT, "close-race"));
            awaitBeforeCommit(concurrentTransfer);
            return concurrentTransfer;
        });

        assertThatThrownBy(() -> transfer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .cause()
                .isInstanceOfSatisfying(TransactionRejectedException.class, rejected ->
                        assertThat(rejected.getReason()).isEqualTo(TransactionFailureReason.ACCOUNT_CLOSED));
        assertThat(balanceOf(sourceId)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("0.00");
    }

    @Test
    void randomConcurrentTransfersKeepEveryBalanceEqualToItsTransactions() throws Exception {
        List<Long> accountIds = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            accountIds.add(createAccount("Owner " + i, "100.00", "TEST-RANDOM-" + i));
        }

        // A fixed seed keeps the requests reproducible; only the thread interleaving changes.
        Random random = new Random(42);
        List<Callable<TransactionResponse>> transfers = new ArrayList<>();
        for (int i = 0; i < RANDOM_TRANSFER_COUNT; i++) {
            int fromIndex = random.nextInt(accountIds.size());
            int toIndex = (fromIndex + 1 + random.nextInt(accountIds.size() - 1)) % accountIds.size();
            BigDecimal amount = BigDecimal.valueOf(random.nextInt(1, 5000), 2);

            transfers.add(transferTask(accountIds.get(fromIndex), accountIds.get(toIndex), amount, "random-" + i));
        }

        int successCount = 0;
        for (Future<TransactionResponse> transfer : executor.invokeAll(transfers)) {
            try {
                transfer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException ex) {
                assertThat(ex.getCause())
                        .isInstanceOfSatisfying(TransactionRejectedException.class, rejected ->
                                assertThat(rejected.getReason())
                                        .isEqualTo(TransactionFailureReason.INSUFFICIENT_FUNDS));
            }
        }

        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(RANDOM_TRANSFER_COUNT);
        assertThat(transactions)
                .filteredOn(transaction -> transaction.getStatus() == TransactionStatus.SUCCESS)
                .hasSize(successCount);

        BigDecimal totalBalance = BigDecimal.ZERO;
        for (Long accountId : accountIds) {
            BigDecimal expectedBalance = new BigDecimal("100.00");
            for (Transaction transaction : transactions) {
                if (transaction.getStatus() != TransactionStatus.SUCCESS) {
                    continue;
                }
                if (transaction.getFromAccount().getId().equals(accountId)) {
                    expectedBalance = expectedBalance.subtract(transaction.getAmount());
                }
                if (transaction.getToAccount().getId().equals(accountId)) {
                    expectedBalance = expectedBalance.add(transaction.getAmount());
                }
            }

            BigDecimal balance = balanceOf(accountId);
            assertThat(balance).isEqualByComparingTo(expectedBalance);
            assertThat(balance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            totalBalance = totalBalance.add(balance);
        }
        assertThat(totalBalance).isEqualByComparingTo("400.00");
    }

    private <T> List<Future<T>> startTogether(List<Callable<T>> tasks) {
        CyclicBarrier startBarrier = new CyclicBarrier(tasks.size());
        return tasks.stream()
                .map(task -> executor.submit(() -> {
                    startBarrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return task.call();
                }))
                .toList();
    }

    // Gives the transfer time to commit if nothing blocks it. Without a row lock it finishes
    // before the surrounding transaction commits, which is the interleaving these tests check.
    private void awaitBeforeCommit(Future<TransactionResponse> transfer) {
        try {
            transfer.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException ignored) {
            // The outcome is checked after the surrounding transaction commits.
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Callable<TransactionResponse> transferTask(Long sourceId,
                                                       Long destinationId,
                                                       BigDecimal amount,
                                                       String idempotencyKey) {
        return () -> transactionService.transfer(
                new TransactionRequest(sourceId, destinationId, amount), idempotencyKey);
    }

    private Long createAccount(String ownerName, String balance, String accountNumber) {
        Account account = new Account(ownerName, accountNumber, new BigDecimal(balance), Currency.GEL);
        return accountRepository.save(account).getId();
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }
}
