package com.lukanizharadze.transferservice;

import com.jayway.jsonpath.JsonPath;
import com.lukanizharadze.transferservice.entity.Transaction;
import com.lukanizharadze.transferservice.model.TransactionFailureReason;
import com.lukanizharadze.transferservice.model.TransactionStatus;
import com.lukanizharadze.transferservice.repository.AccountRepository;
import com.lukanizharadze.transferservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class FailedTransferIntegrationTest {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final String OPEN_ACCOUNT_BODY = """
            {
              "ownerName": "%s",
              "initialBalance": %s,
              "currency": "%s"
            }
            """;

    private static final String TRANSFER_BODY = """
            {
              "fromAccountId": %d,
              "toAccountId": %d,
              "amount": %s
            }
            """;

    @Autowired
    private MockMvc mockMvc;

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
    void insufficientFundsStoresFailedTransaction() throws Exception {
        Long sourceId = openAccount("Source Owner", "100.00", "GEL");
        Long destinationId = openAccount("Destination Owner", "0.00", "GEL");

        transfer(sourceId, destinationId, "500.00", "failed-insufficient")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("0.00");

        Transaction rejected = singleStoredTransaction();
        assertThat(rejected.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(rejected.getFailureReason()).isEqualTo(TransactionFailureReason.INSUFFICIENT_FUNDS);
        assertThat(rejected.getIdempotencyKey()).isEqualTo("failed-insufficient");
        assertThat(rejected.getFromAccount().getId()).isEqualTo(sourceId);
        assertThat(rejected.getToAccount().getId()).isEqualTo(destinationId);
        assertThat(rejected.getAmount()).isEqualByComparingTo("500.00");
        assertThat(rejected.getCreatedAt()).isNotNull();
    }

    @Test
    void currencyMismatchStoresFailedTransaction() throws Exception {
        Long sourceId = openAccount("Source Owner", "1000.00", "GEL");
        Long destinationId = openAccount("Destination Owner", "0.00", "USD");

        transfer(sourceId, destinationId, "100.00", "failed-currency")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("0.00");

        Transaction rejected = singleStoredTransaction();
        assertThat(rejected.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(rejected.getFailureReason()).isEqualTo(TransactionFailureReason.CURRENCY_MISMATCH);
    }

    @Test
    void closedAccountStoresFailedTransaction() throws Exception {
        Long sourceId = openAccount("Source Owner", "1000.00", "GEL");
        Long destinationId = openAccount("Destination Owner", "0.00", "GEL");

        mockMvc.perform(delete("/api/v1/accounts/{accountId}", destinationId))
                .andExpect(status().isNoContent());

        transfer(sourceId, destinationId, "100.00", "failed-closed")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CLOSED"));

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("0.00");

        Transaction rejected = singleStoredTransaction();
        assertThat(rejected.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(rejected.getFailureReason()).isEqualTo(TransactionFailureReason.ACCOUNT_CLOSED);
    }

    @Test
    void replayingFailedTransferReturnsSameRejectionWithoutStoringAnotherRow() throws Exception {
        Long sourceId = openAccount("Source Owner", "100.00", "GEL");
        Long destinationId = openAccount("Destination Owner", "0.00", "GEL");

        transfer(sourceId, destinationId, "500.00", "failed-replayed")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        Long storedId = singleStoredTransaction().getId();

        transfer(sourceId, destinationId, "500.00", "failed-replayed")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("0.00");
        assertThat(singleStoredTransaction().getId()).isEqualTo(storedId);
    }

    @Test
    void reusingFailedKeyWithDifferentAmountIsRejected() throws Exception {
        Long sourceId = openAccount("Source Owner", "100.00", "GEL");
        Long destinationId = openAccount("Destination Owner", "0.00", "GEL");

        transfer(sourceId, destinationId, "500.00", "failed-conflict")
                .andExpect(status().isUnprocessableContent());

        transfer(sourceId, destinationId, "600.00", "failed-conflict")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        Transaction rejected = singleStoredTransaction();
        assertThat(rejected.getFailureReason()).isEqualTo(TransactionFailureReason.INSUFFICIENT_FUNDS);
        assertThat(rejected.getAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void requestsThatAreNotBusinessAttemptsStoreNothing() throws Exception {
        Long sourceId = openAccount("Source Owner", "100.00", "GEL");

        transfer(sourceId, sourceId, "10.00", "not-attempted-same-account")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSACTION"));

        transfer(sourceId, 9999L, "10.00", "not-attempted-missing-account")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("100.00");
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    private Transaction singleStoredTransaction() {
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);

        return transactions.getFirst();
    }

    private Long openAccount(String ownerName, String initialBalance, String currency) throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_ACCOUNT_BODY.formatted(ownerName, initialBalance, currency)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions transfer(Long fromAccountId, Long toAccountId, String amount, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/v1/transactions")
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TRANSFER_BODY.formatted(fromAccountId, toAccountId, amount)));
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }
}
