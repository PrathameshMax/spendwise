package com.spendwise.transactionservice.service;

import com.spendwise.transactionservice.api.MonthlyInsightsResponse;
import com.spendwise.transactionservice.domain.InsightsRepository;
import com.spendwise.transactionservice.domain.SpendTotal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InsightsServiceTest {

    private static final String USER = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";

    @Test
    void splitsIncomeFromSpendingAndComputesCategoryShares() {
        InsightsRepository repository = repository(
                List.of(row("Salary", "INCOME", "85000.00", 1),
                        row("Rent", "EXPENSE", "24000.00", 1),
                        row("Groceries", "EXPENSE", "12000.00", 9),
                        row("Food & Dining", "EXPENSE", "4000.00", 14),
                        row("Refunds & Cashback", "INCOME", "250.00", 2)),
                List.of(row("BIGBASKET", "EXPENSE", "7000.00", 4),
                        row("SWIGGY", "EXPENSE", "2500.00", 8)));

        MonthlyInsightsResponse insights = new InsightsService(repository).monthly(USER, YearMonth.of(2026, 10));

        assertThat(insights.userId()).isEqualTo(USER);
        assertThat(insights.month()).isEqualTo("2026-10");
        assertThat(insights.totalSpent()).isEqualByComparingTo("40000.00");
        assertThat(insights.totalIncome()).isEqualByComparingTo("85250.00");
        assertThat(insights.expenseCount()).isEqualTo(24);
        assertThat(insights.categories()).extracting(MonthlyInsightsResponse.CategorySpend::category)
                .containsExactly("Rent", "Groceries", "Food & Dining");
        assertThat(insights.categories().get(0).sharePercent()).isEqualByComparingTo("60.0");
        assertThat(insights.categories().get(2).sharePercent()).isEqualByComparingTo("10.0");
        assertThat(insights.topMerchants()).extracting(MonthlyInsightsResponse.MerchantSpend::merchant)
                .containsExactly("BIGBASKET", "SWIGGY");
    }

    @Test
    void emptyMonthHasZeroTotalsAndNoShares() {
        MonthlyInsightsResponse insights = new InsightsService(repository(List.of(), List.of()))
                .monthly(USER, YearMonth.of(2026, 1));

        assertThat(insights.totalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(insights.totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(insights.expenseCount()).isZero();
        assertThat(insights.categories()).isEmpty();
        assertThat(insights.topMerchants()).isEmpty();
    }

    private static InsightsRepository repository(List<SpendTotal> byCategory, List<SpendTotal> merchants) {
        return new InsightsRepository() {
            @Override
            public List<SpendTotal> totalsByCategory(UUID userId, LocalDate monthStart, LocalDate nextMonthStart) {
                return byCategory;
            }

            @Override
            public List<SpendTotal> topUpiMerchants(UUID userId, LocalDate monthStart, LocalDate nextMonthStart) {
                return merchants;
            }
        };
    }

    private static SpendTotal row(String label, String type, String total, long entries) {
        return new SpendTotal() {
            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public String getType() {
                return type;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }

            @Override
            public Long getEntries() {
                return entries;
            }
        };
    }
}
