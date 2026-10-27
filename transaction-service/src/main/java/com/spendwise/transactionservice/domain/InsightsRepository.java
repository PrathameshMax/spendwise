package com.spendwise.transactionservice.domain;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregations behind the home screen's monthly insights card.
 * Only POSTED entries count: a reversed transaction and its reversing entry
 * are both excluded, so the month shows what the user actually spent.
 */
public interface InsightsRepository extends Repository<Transaction, UUID> {

    @Query(value = """
            SELECT c.name AS label, t.type AS type, SUM(t.amount) AS total, COUNT(*) AS entries
            FROM transactions t
            JOIN categories c ON c.id = t.category_id
            WHERE CAST(t.user_id AS varchar) = :userId
              AND to_char(t.transaction_date, 'YYYY-MM') = :month
              AND t.status = 'POSTED'
            GROUP BY c.name, t.type
            ORDER BY total DESC
            """, nativeQuery = true)
    List<SpendTotal> totalsByCategory(@Param("userId") String userId, @Param("month") String month);

    @Query(value = """
            SELECT split_part(t.description, '/', 2) AS label, t.type AS type, SUM(t.amount) AS total, COUNT(*) AS entries
            FROM transactions t
            WHERE CAST(t.user_id AS varchar) = :userId
              AND to_char(t.transaction_date, 'YYYY-MM') = :month
              AND t.status = 'POSTED'
              AND t.type = 'EXPENSE'
              AND t.description LIKE 'UPI/%'
            GROUP BY split_part(t.description, '/', 2), t.type
            ORDER BY total DESC
            LIMIT 5
            """, nativeQuery = true)
    List<SpendTotal> topUpiMerchants(@Param("userId") String userId, @Param("month") String month);
}
