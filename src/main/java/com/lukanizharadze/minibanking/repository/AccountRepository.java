package com.lukanizharadze.minibanking.repository;


import com.lukanizharadze.minibanking.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AccountRepository extends JpaRepository<Account, Long> {
}
