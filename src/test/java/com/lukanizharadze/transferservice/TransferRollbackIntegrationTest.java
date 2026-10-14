package com.lukanizharadze.transferservice;

import com.lukanizharadze.transferservice.dto.TransactionRequest;
import com.lukanizharadze.transferservice.entity.Account;
import com.lukanizharadze.transferservice.entity.Transaction;
import com.lukanizharadze.transferservice.mapper.TransactionMapper;
import com.lukanizharadze.transferservice.model.Currency;
import com.lukanizharadze.transferservice.repository.AccountRepository;
import com.lukanizharadze.transferservice.repository.TransactionRepository;
import com.lukanizharadze.transferservice.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TransferRollbackIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockitoSpyBean
    private TransactionMapper transactionMapper;

    @BeforeEach
    void clearDatabase() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void unexpectedFailureAfterFlushRollsBackTheWholeTransfer() {
        Long sourceId = createAccount("Source Owner", "1000.00", "ROLLBACK-SOURCE");
        Long destinationId = createAccount("Destination Owner", "0.00", "ROLLBACK-DESTINATION");

        // The mapper runs after saveAndFlush, so Hibernate has already sent both balance
        // updates and the SUCCESS insert to the database when this exception is thrown.
        doThrow(new IllegalStateException("simulated response mapping failure"))
                .when(transactionMapper).toResponse(any(Transaction.class));

        assertThatThrownBy(() -> transactionService.transfer(
                new TransactionRequest(sourceId, destinationId, new BigDecimal("250.00")),
                "rollback-after-flush"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated response mapping failure");

        assertThat(balanceOf(sourceId)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("0.00");
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    private Long createAccount(String ownerName, String balance, String accountNumber) {
        Account account = new Account(ownerName, accountNumber, new BigDecimal(balance), Currency.GEL);
        return accountRepository.save(account).getId();
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }
}
