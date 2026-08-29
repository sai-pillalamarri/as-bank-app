package com.asbank.transaction.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByAccountId(
            UUID accountId,
            Pageable pageable
    );

    boolean existsByTransactionId(UUID transactionId);
}