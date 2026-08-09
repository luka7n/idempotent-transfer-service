package com.lukanizharadze.minibanking.entity;
import com.lukanizharadze.minibanking.model.AccountStatus;
import com.lukanizharadze.minibanking.model.Currency;
import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;


@Entity
@Getter
@Table(name="accounts", check =
@CheckConstraint(name = "check_account_balance_is_not_negative", constraint = "balance >= 0"))
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="owner_name", nullable = false, length = 50)
    private String ownerName;
    @Column(name="account_number", nullable = false, unique = true, updatable = false, length = 20)
    private String accountNumber;
    @Column(name = "balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false, length = 16)
    private AccountStatus status;

    @Column(name="created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }
    public Account(String ownerName, String accountNumber, BigDecimal initialBalance, Currency currency) {
        this.ownerName = ownerName;
        this.accountNumber = accountNumber;
        this.balance = initialBalance;
        this.currency = currency;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void updateOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public void debit(BigDecimal amount) {
        balance = balance.subtract(amount);
    }


    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }
    public void close() {
        status = AccountStatus.CLOSED;
    }



}
