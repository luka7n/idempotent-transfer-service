package com.lukanizharadze.transferservice;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountHistoryIntegrationTest {

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
    void historyContainsIncomingOutgoingAndFailedTransfersNewestFirst() throws Exception {
        Long accountId = openAccount("History Owner", "1000.00");
        Long firstOtherId = openAccount("First Other", "0.00");
        Long secondOtherId = openAccount("Second Other", "500.00");

        transfer(accountId, firstOtherId, "100.00", "history-outgoing").andExpect(status().isCreated());
        transfer(secondOtherId, accountId, "50.00", "history-incoming").andExpect(status().isCreated());
        transfer(secondOtherId, firstOtherId, "10.00", "history-unrelated").andExpect(status().isCreated());
        transfer(accountId, secondOtherId, "5000.00", "history-failed")
                .andExpect(status().isUnprocessableContent());

        String history = fetch(get("/api/v1/accounts/{accountId}/transactions", accountId));

        assertThat(JsonPath.<Integer>read(history, "$.totalElements")).isEqualTo(3);
        assertThat(JsonPath.<List<String>>read(history, "$.transactions[*].status"))
                .containsExactly("FAILED", "SUCCESS", "SUCCESS");
        assertThat(readAmounts(history))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("5000.00"), new BigDecimal("50.00"), new BigDecimal("100.00"));
    }

    @Test
    void historyIsPaginatedAndFilteredByCreationTime() throws Exception {
        Long sourceId = openAccount("Source Owner", "1000.00");
        Long destinationId = openAccount("Destination Owner", "0.00");
        for (int amount = 1; amount <= 5; amount++) {
            transfer(sourceId, destinationId, amount + ".00", "history-page-" + amount)
                    .andExpect(status().isCreated());
        }

        String secondPage = fetch(get("/api/v1/accounts/{accountId}/transactions", sourceId)
                .param("page", "1")
                .param("size", "2"));

        assertThat(JsonPath.<Integer>read(secondPage, "$.page")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(secondPage, "$.size")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(secondPage, "$.totalElements")).isEqualTo(5);
        assertThat(JsonPath.<Integer>read(secondPage, "$.totalPages")).isEqualTo(3);
        assertThat(readAmounts(secondPage))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3.00"), new BigDecimal("2.00"));

        // Newest first, so index 3 is the second transfer and index 1 is the fourth.
        List<String> createdAt = JsonPath.read(
                fetch(get("/api/v1/accounts/{accountId}/transactions", sourceId)),
                "$.transactions[*].createdAt");

        String filtered = fetch(get("/api/v1/accounts/{accountId}/transactions", sourceId)
                .param("from", createdAt.get(3))
                .param("to", createdAt.get(1)));

        assertThat(readAmounts(filtered))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3.00"), new BigDecimal("2.00"));
    }

    @Test
    void topAccountsCountSuccessfulTransfersOnBothSidesAndIgnoreFailedOnes() throws Exception {
        Long first = openAccount("First Owner", "1000.00");
        Long second = openAccount("Second Owner", "1000.00");
        Long third = openAccount("Third Owner", "1000.00");
        Long fourth = openAccount("Fourth Owner", "1000.00");
        Long fifth = openAccount("Fifth Owner", "1000.00");
        Long sixth = openAccount("Sixth Owner", "1000.00");

        transfer(first, second, "10.00", "top-1").andExpect(status().isCreated());
        transfer(first, third, "10.00", "top-2").andExpect(status().isCreated());
        transfer(first, fourth, "10.00", "top-3").andExpect(status().isCreated());
        transfer(second, third, "10.00", "top-4").andExpect(status().isCreated());
        transfer(fifth, first, "10.00", "top-5").andExpect(status().isCreated());
        transfer(sixth, fifth, "10.00", "top-6").andExpect(status().isCreated());

        // If failed transfers were counted, the fourth and sixth accounts would move up.
        transfer(fourth, sixth, "9000.00", "top-failed-1").andExpect(status().isUnprocessableContent());
        transfer(sixth, fourth, "9000.00", "top-failed-2").andExpect(status().isUnprocessableContent());

        String top = fetch(get("/api/v1/accounts/top"));

        assertThat(JsonPath.<List<Number>>read(top, "$[*].accountId"))
                .extracting(Number::longValue)
                .containsExactly(first, second, third, fifth, fourth);
        assertThat(JsonPath.<List<Number>>read(top, "$[*].transactionCount"))
                .extracting(Number::longValue)
                .containsExactly(4L, 2L, 2L, 2L, 1L);
    }

    private String fetch(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private List<BigDecimal> readAmounts(String history) {
        List<Number> amounts = JsonPath.read(history, "$.transactions[*].amount");
        return amounts.stream()
                .map(amount -> new BigDecimal(amount.toString()))
                .toList();
    }

    private Long openAccount(String ownerName, String initialBalance) throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_ACCOUNT_BODY.formatted(ownerName, initialBalance)))
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
}
