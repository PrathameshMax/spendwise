package com.spendwise.transactionservice.service;

import com.spendwise.transactionservice.api.MonthlyInsightsResponse;
import com.spendwise.transactionservice.api.MonthlyInsightsResponse.CategorySpend;
import com.spendwise.transactionservice.api.MonthlyInsightsResponse.MerchantSpend;
import com.spendwise.transactionservice.domain.InsightsRepository;
import com.spendwise.transactionservice.domain.SpendTotal;
import com.spendwise.transactionservice.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;


@Service
public class InsightsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InsightsRepository insightsRepository;

    public InsightsService(InsightsRepository insightsRepository) {
        this.insightsRepository = insightsRepository;
    }

    @Transactional(readOnly = true)
    public MonthlyInsightsResponse monthly(UUID userId, YearMonth month) {
        String period = month.toString();
        LocalDate monthStart = month.atDay(1);
        LocalDate nextMonthStart = month.plusMonths(1).atDay(1);

        List<SpendTotal> byCategory = insightsRepository.totalsByCategory(userId, monthStart, nextMonthStart);
        List<SpendTotal> merchants = insightsRepository.topUpiMerchants(userId, monthStart, nextMonthStart);

        List<SpendTotal> expenses = byCategory.stream()
                .filter(row -> TransactionType.EXPENSE.name().equals(row.getType()))
                .toList();
        BigDecimal totalSpent = sum(expenses);
        BigDecimal totalIncome = sum(byCategory.stream()
                .filter(row -> TransactionType.INCOME.name().equals(row.getType()))
                .toList());
        long expenseCount = expenses.stream().mapToLong(row -> count(row)).sum();

        List<CategorySpend> categories = expenses.stream()
                .map(row -> new CategorySpend(row.getLabel(), amount(row), count(row), share(amount(row), totalSpent)))
                .toList();
        List<MerchantSpend> topMerchants = merchants.stream()
                .map(row -> new MerchantSpend(row.getLabel(), amount(row), count(row)))
                .toList();

        return new MonthlyInsightsResponse(userId, period, totalSpent, totalIncome, expenseCount,
                categories, topMerchants);
    }

    private static BigDecimal sum(List<SpendTotal> rows) {
        return rows.stream().map(InsightsService::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal amount(SpendTotal row) {
        return row.getTotal() == null ? BigDecimal.ZERO : row.getTotal();
    }

    private static long count(SpendTotal row) {
        return row.getEntries() == null ? 0L : row.getEntries();
    }

    private static BigDecimal share(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
