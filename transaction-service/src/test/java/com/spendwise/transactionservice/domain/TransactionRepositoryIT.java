package com.spendwise.transactionservice.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void filtersByCategoryAndDateRangeAndType() {
        UUID userId = UUID.randomUUID();
        Category electronics = categoryRepository.save(new Category("Electronics", false));
        Category dining = categoryRepository.save(new Category("Dining", false));

        transactionRepository.save(new Transaction(userId, electronics, new BigDecimal("12000.00"),
                TransactionType.EXPENSE, "Headphones", LocalDate.of(2026, 9, 10)));
        transactionRepository.save(new Transaction(userId, dining, new BigDecimal("800.00"),
                TransactionType.EXPENSE, "Dinner", LocalDate.of(2026, 9, 12)));
        transactionRepository.save(new Transaction(userId, electronics, new BigDecimal("3000.00"),
                TransactionType.EXPENSE, "Cable", LocalDate.of(2026, 8, 1)));

        var spec = TransactionSpecifications.combine(
                TransactionSpecifications.hasUserId(userId),
                TransactionSpecifications.hasCategoryId(electronics.getId()),
                TransactionSpecifications.dateFrom(LocalDate.of(2026, 9, 1)),
                TransactionSpecifications.dateTo(LocalDate.of(2026, 9, 30)));

        var page = transactionRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getDescription()).isEqualTo("Headphones");
    }

    @Test
    void unfilteredSpecificationReturnsEverything() {
        Category category = categoryRepository.save(new Category("Groceries", false));
        transactionRepository.save(new Transaction(UUID.randomUUID(), category, BigDecimal.TEN,
                TransactionType.EXPENSE, "Milk", LocalDate.now()));

        var spec = TransactionSpecifications.combine(null, null, null, null, null, null, null);
        var page = transactionRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getContent()).isNotEmpty();
    }
}
