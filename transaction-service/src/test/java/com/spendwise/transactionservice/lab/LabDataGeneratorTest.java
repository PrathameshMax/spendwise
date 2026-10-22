package com.spendwise.transactionservice.lab;

import com.spendwise.transactionservice.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LabDataGeneratorTest {

    private static final LocalDate LAST_DATE = LocalDate.of(2026, 10, 21);

    @Test
    void sameSeedProducesIdenticalLedger() {
        List<LabTransactionRow> first = generate(new LabDataGenerator(42L, 6, LAST_DATE), 7);
        List<LabTransactionRow> second = generate(new LabDataGenerator(42L, 6, LAST_DATE), 7);
        List<LabTransactionRow> otherSeed = generate(new LabDataGenerator(43L, 6, LAST_DATE), 7);

        assertThat(second).isEqualTo(first);
        assertThat(otherSeed).isNotEqualTo(first);
    }

    @Test
    void everyRowFitsTheTransactionsTableAndTheDateWindow() {
        LabDataGenerator generator = new LabDataGenerator(42L, 12, LAST_DATE);
        List<LabTransactionRow> rows = new ArrayList<>();
        for (int user = 1; user <= 50; user++) {
            generator.generateUser(user, rows::add);
        }

        assertThat(generator.firstDate()).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.transactionDate()).isBetween(generator.firstDate(), LAST_DATE);
            assertThat(row.createdAt().toLocalDate()).isEqualTo(row.transactionDate());
            assertThat(row.amount()).isPositive();
            assertThat(row.amount().scale()).isEqualTo(2);
            assertThat(row.amount().precision()).isLessThanOrEqualTo(19);
            assertThat(row.description()).isNotBlank().hasSizeLessThanOrEqualTo(500);
            assertThat(LabIds.isLabId(row.userId())).isTrue();
            assertThat(LabIds.isLabId(row.id())).isFalse();
            assertThat(row.id().version()).isEqualTo(4);
        });
        Set<UUID> ids = new HashSet<>();
        rows.forEach(row -> ids.add(row.id()));
        assertThat(ids).hasSize(rows.size());
    }

    @Test
    void salaryIsCreditedOnTheFirstOfEveryMonth() {
        LabDataGenerator generator = new LabDataGenerator(7L, 12, LAST_DATE);
        List<LabTransactionRow> rows = generate(generator, 1);

        List<LabTransactionRow> salaries = rows.stream()
                .filter(row -> row.category() == LabCategory.SALARY)
                .toList();
        assertThat(salaries).hasSize(12);
        assertThat(salaries).allSatisfy(row -> {
            assertThat(row.type()).isEqualTo(TransactionType.INCOME);
            assertThat(row.transactionDate().getDayOfMonth()).isEqualTo(1);
            assertThat(row.description()).startsWith("NEFT/SALARY ");
        });
        BigDecimal march = salaryFor(salaries, YearMonth.of(2026, 3));
        BigDecimal april = salaryFor(salaries, YearMonth.of(2026, 4));
        assertThat(april).isGreaterThan(march);
    }

    @Test
    void currentMonthStopsAtTheLastDate() {
        LabDataGenerator generator = new LabDataGenerator(42L, 1, LocalDate.of(2026, 10, 5));
        List<LabTransactionRow> rows = generate(generator, 20);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row ->
                assertThat(row.transactionDate()).isBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
    }

    @Test
    void volumePerUserMonthStaysWithinTheSizingEstimate() {
        LabDataGenerator generator = new LabDataGenerator(42L, 12, LocalDate.of(2026, 9, 30));
        List<LabTransactionRow> rows = generate(generator, 200);

        double perUserMonth = rows.size() / (200.0 * 12);
        assertThat(perUserMonth).isBetween(25.0, (double) LabDataGenerator.ESTIMATED_ROWS_PER_USER_MONTH);
        assertThat(rows.stream().map(LabTransactionRow::category).distinct().count())
                .isEqualTo(LabCategory.values().length);
    }

    private static List<LabTransactionRow> generate(LabDataGenerator generator, int users) {
        List<LabTransactionRow> rows = new ArrayList<>();
        for (int user = 1; user <= users; user++) {
            generator.generateUser(user, rows::add);
        }
        return rows;
    }

    private static BigDecimal salaryFor(List<LabTransactionRow> salaries, YearMonth month) {
        return salaries.stream()
                .filter(row -> YearMonth.from(row.transactionDate()).equals(month))
                .findFirst()
                .orElseThrow()
                .amount();
    }
}
