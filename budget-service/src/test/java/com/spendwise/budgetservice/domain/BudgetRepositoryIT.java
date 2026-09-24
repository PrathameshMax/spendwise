package com.spendwise.budgetservice.domain;

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
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BudgetRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private BudgetRepository budgetRepository;

    @Test
    void newBudgetStartsWithZeroCurrentSpend() {
        UUID userId = UUID.randomUUID();
        Budget saved = budgetRepository.save(
                new Budget(userId, "Electronics", new BigDecimal("10000.00"), YearMonth.of(2026, 9)));

        assertThat(saved.getCurrentSpend()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void enforcesUniqueUserCategoryPeriodCombination() {
        UUID userId = UUID.randomUUID();
        budgetRepository.save(new Budget(userId, "Dining", new BigDecimal("5000.00"), YearMonth.of(2026, 9)));

        assertThat(budgetRepository.existsByUserIdAndCategoryAndPeriodMonth(
                userId, "Dining", YearMonth.of(2026, 9))).isTrue();
    }

    @Test
    void findsAllBudgetsForAUserAndMonth() {
        UUID userId = UUID.randomUUID();
        budgetRepository.save(new Budget(userId, "Dining", new BigDecimal("5000.00"), YearMonth.of(2026, 9)));
        budgetRepository.save(new Budget(userId, "Electronics", new BigDecimal("10000.00"), YearMonth.of(2026, 9)));
        budgetRepository.save(new Budget(userId, "Dining", new BigDecimal("4000.00"), YearMonth.of(2026, 10)));

        List<Budget> september = budgetRepository.findByUserIdAndPeriodMonth(userId, YearMonth.of(2026, 9));

        assertThat(september).hasSize(2);
    }
}
