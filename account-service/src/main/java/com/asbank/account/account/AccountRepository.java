package com.asbank.account.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Page<Account> findByCustomerId(
            UUID customerId,
            Pageable pageable
    );
}