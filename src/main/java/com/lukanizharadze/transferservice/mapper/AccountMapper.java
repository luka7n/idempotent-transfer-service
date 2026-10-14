package com.lukanizharadze.transferservice.mapper;

import com.lukanizharadze.transferservice.dto.AccountResponse;
import com.lukanizharadze.transferservice.entity.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
