package com.spendwise.transactionservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>,
        JpaSpecificationExecutor<Transaction> {

    /**
     * Ownership is part of the query ({@code WHERE id = ? AND user_id = ?}), not a
     * check after loading: another user's row is never read into memory, and the
     * caller cannot distinguish "not yours" from "does not exist".
     */
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);
}
