package com.spendwise.transactionservice.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InsightsRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private InsightsRepository insightsRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void aggregatesOneUsersMonthByCategoryAndUpiMerchant() {
        UUID userId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Category groceries = categoryRepository.save(new Category("Insights Groceries " + userId, false));
        Category salary = categoryRepository.save(new Category("Insights Salary " + userId, false));

        save(userId, groceries, "1200.00", TransactionType.EXPENSE, "UPI/BIGBASKET/111/GPay", LocalDate.of(2026, 10, 3));
        save(userId, groceries, "300.00", TransactionType.EXPENSE, "UPI/BLINKIT/112/GPay", LocalDate.of(2026, 10, 9));
        save(userId, groceries, "800.00", TransactionType.EXPENSE, "UPI/BIGBASKET/113/GPay", LocalDate.of(2026, 10, 20));
        save(userId, salary, "85000.00", TransactionType.INCOME, "NEFT/SALARY OCT-2026/INFOSYS LTD", LocalDate.of(2026, 10, 1));
        save(userId, groceries, "999.00", TransactionType.EXPENSE, "UPI/BIGBASKET/114/GPay", LocalDate.of(2026, 9, 30));
        save(otherUser, groceries, "5000.00", TransactionType.EXPENSE, "UPI/DMART/115/GPay", LocalDate.of(2026, 10, 5));

        List<SpendTotal> byCategory = insightsRepository.totalsByCategory(userId.toString(), "2026-10");
        List<SpendTotal> merchants = insightsRepository.topUpiMerchants(userId.toString(), "2026-10");

        assertThat(byCategory).hasSize(2);
        assertThat(byCategory.get(0).getLabel()).isEqualTo(salary.getName());
        assertThat(byCategory.get(0).getType()).isEqualTo("INCOME");
        assertThat(byCategory.get(0).getTotal()).isEqualByComparingTo("85000.00");
        assertThat(byCategory.get(1).getLabel()).isEqualTo(groceries.getName());
        assertThat(byCategory.get(1).getTotal()).isEqualByComparingTo("2300.00");
        assertThat(byCategory.get(1).getEntries()).isEqualTo(3L);

        assertThat(merchants).extracting(SpendTotal::getLabel).containsExactly("BIGBASKET", "BLINKIT");
        assertThat(merchants.get(0).getTotal()).isEqualByComparingTo("2000.00");
        assertThat(merchants.get(0).getEntries()).isEqualTo(2L);
    }

    private void save(UUID userId, Category category, String amount, TransactionType type, String description,
                      LocalDate date) {
        transactionRepository.save(new Transaction(userId, category, new BigDecimal(amount), type, description, date,
                "INR", null));
    }
}
