package com.lukanizharadze.minibanking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;

import com.lukanizharadze.minibanking.model.TransactionStatus;
import com.lukanizharadze.minibanking.model.TransactionFailureReason;
import java.time.Instant;

@Entity
@Getter
@Table(
        name = "transactions", check = @CheckConstraint(
                name = "check_transaction_amount_is_positive",
                constraint = "amount > 0"
        )
)
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false, updatable = false)
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false, updatable = false)
    private Account toAccount;

    @Column(
            name = "amount",
            nullable = false,
            updatable = false,
            precision = 20,
            scale = 2
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 16)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30, updatable = false)
    private TransactionFailureReason failureReason;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected Transaction() {
    }




    public Transaction(Account fromAccount, Account toAccount, BigDecimal amount) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.status = TransactionStatus.SUCCESS;
        this.createdAt = Instant.now();
    }
}
