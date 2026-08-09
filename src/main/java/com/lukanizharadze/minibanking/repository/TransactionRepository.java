package com.lukanizharadze.minibanking.repository;

import com.lukanizharadze.minibanking.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

}
