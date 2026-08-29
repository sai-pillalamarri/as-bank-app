package com.asbank.account.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface BalanceCommandRepository
        extends JpaRepository<BalanceCommand, UUID> {
}