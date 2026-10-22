package com.spendwise.transactionservice.lab;

import java.time.LocalDate;

/**
 * What is in the database right now: lab rows versus everything else, and how
 * big the transactions table has grown (indexes included).
 */
public record LabDataSummary(
        long labUsers,
        long labTransactions,
        long labCategories,
        long allTransactions,
        LocalDate labFirstDate,
        LocalDate labLastDate,
        String transactionsTableSize) {
}
