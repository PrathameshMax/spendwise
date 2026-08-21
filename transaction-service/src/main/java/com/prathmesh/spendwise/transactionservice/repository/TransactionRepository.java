package com.prathmesh.spendwise.transactionservice.repository;

import com.prathmesh.spendwise.transactionservice.entity.Transaction;
import com.prathmesh.spendwise.transactionservice.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByUserId(
            Long userId,
            Pageable pageable
    );

    List<Transaction> findByUserIdAndType(
            Long userId,
            TransactionType type
    );

    Page<Transaction> findByUserIdAndType(
            Long userId,
            TransactionType type,
            Pageable pageable
    );

    List<Transaction> findByType(
            TransactionType type
    );

    Page<Transaction> findByType(
            TransactionType type,
            Pageable pageable
    );
}