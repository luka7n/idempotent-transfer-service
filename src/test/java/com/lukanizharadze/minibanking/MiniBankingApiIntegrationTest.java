package com.lukanizharadze.minibanking;

import com.jayway.jsonpath.JsonPath;
import com.lukanizharadze.minibanking.entity.Account;
import com.lukanizharadze.minibanking.entity.Transaction;
import com.lukanizharadze.minibanking.model.AccountStatus;
import com.lukanizharadze.minibanking.model.Currency;
import com.lukanizharadze.minibanking.model.TransactionFailureReason;
import com.lukanizharadze.minibanking.model.TransactionStatus;
import com.lukanizharadze.minibanking.repository.AccountRepository;
import com.lukanizharadze.minibanking.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MiniBankingApiIntegrationTest {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final String OPEN_ACCOUNT_BODY = """
            {
              "ownerName": "%s",
              "initialBalance": %s,
              "currency": "GEL"
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
    void openAccountReturnsCreatedAndPersistsAccount() throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_ACCOUNT_BODY.formatted("Luka Nizharadze", "1000.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ownerName").value("Luka Nizharadze"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Account persisted = accountRepository.findById(readId(response)).orElseThrow();

        assertThat(persisted.getOwnerName()).isEqualTo("Luka Nizharadze");
        assertThat(persisted.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(persisted.getCurrency()).isEqualTo(Currency.GEL);
        assertThat(persisted.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(persisted.getAccountNumber()).isNotBlank();
    }

    @Test
    void successfulTransferMovesMoneyAndStoresOneSuccessfulTransaction() throws Exception {
        Long sourceId = openAccount("Source Owner", "1000.00");
        Long destinationId = openAccount("Destination Owner", "0.00");

        transfer(sourceId, destinationId, "250.00", "transfer-success")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.fromAccountId").value(sourceId))
                .andExpect(jsonPath("$.toAccountId").value(destinationId));

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("750.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("250.00");

        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);

        Transaction stored = transactions.getFirst();
        assertThat(stored.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(stored.getFailureReason()).isNull();
        assertThat(stored.getAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void insufficientFundsIsRejectedAndLeavesBalancesUnchanged() throws Exception {
        Long sourceId = openAccount("Source Owner", "100.00");
        Long destinationId = openAccount("Destination Owner", "0.00");

        transfer(sourceId, destinationId, "500.00", "transfer-insufficient")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("0.00");

        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);

        Transaction rejected = transactions.getFirst();
        assertThat(rejected.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(rejected.getFailureReason()).isEqualTo(TransactionFailureReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void repeatedIdempotencyKeyReturnsFirstTransactionAndDebitsOnce() throws Exception {
        Long sourceId = openAccount("Source Owner", "1000.00");
        Long destinationId = openAccount("Destination Owner", "0.00");

        String firstResponse = transfer(sourceId, destinationId, "200.00", "transfer-repeated")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String repeatedResponse = transfer(sourceId, destinationId, "200.00", "transfer-repeated")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(readId(repeatedResponse)).isEqualTo(readId(firstResponse));
        assertThat(balanceOf(sourceId)).isEqualByComparingTo("800.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("200.00");
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentAmountIsRejected() throws Exception {
        Long sourceId = openAccount("Source Owner", "1000.00");
        Long destinationId = openAccount("Destination Owner", "0.00");

        transfer(sourceId, destinationId, "200.00", "transfer-conflict")
                .andExpect(status().isCreated());

        transfer(sourceId, destinationId, "300.00", "transfer-conflict")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("800.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("200.00");
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    private Long openAccount(String ownerName, String initialBalance) throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_ACCOUNT_BODY.formatted(ownerName, initialBalance)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return readId(response);
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

    private Long readId(String response) {
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}
