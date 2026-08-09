package com.lukanizharadze.minibanking.repository;

import com.lukanizharadze.minibanking.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {


    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

}
