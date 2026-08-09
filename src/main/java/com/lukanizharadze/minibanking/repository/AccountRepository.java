package com.lukanizharadze.minibanking.repository;


import com.lukanizharadze.minibanking.entity.Account;
import com.lukanizharadze.minibanking.model.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findAccountsByStatus(AccountStatus status);

}
