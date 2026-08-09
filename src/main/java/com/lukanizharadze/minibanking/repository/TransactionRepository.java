package com.lukanizharadze.minibanking.repository;

import com.lukanizharadze.minibanking.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {


    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        select tx
        from Transaction tx
        where (tx.fromAccount.id = :accountId
               or tx.toAccount.id = :accountId)
          and (:fromDate is null or tx.createdAt >= :fromDate)
          and (:toDate is null or tx.createdAt < :toDate)
        order by tx.createdAt desc, tx.id desc
        """)
    Page<Transaction> findHistory(
            @Param("accountId") Long accountId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );
}
