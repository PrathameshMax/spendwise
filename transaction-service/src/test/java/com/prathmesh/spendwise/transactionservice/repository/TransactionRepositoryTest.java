package com.prathmesh.spendwise.transactionservice.repository;

import com.prathmesh.spendwise.transactionservice.entity.Transaction;
import com.prathmesh.spendwise.transactionservice.entity.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
public class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }


    // --------------------------------------------------
    // SAVE
    // --------------------------------------------------

    @Test
    void save_shouldPersistTransaction() {

        Transaction transaction = createTransaction(
                1L,
                "5000.00",
                TransactionType.INCOME,
                "Salary",
                "Monthly salary"
        );

        Transaction saved =
                transactionRepository.saveAndFlush(transaction);

        assertNotNull(saved.getId());
        assertEquals(1L, saved.getUserId());

        assertEquals(
                new BigDecimal("5000.00"),
                saved.getAmount()
        );

        assertEquals(
                TransactionType.INCOME,
                saved.getType()
        );

        assertEquals(
                "Salary",
                saved.getCategory()
        );

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }


    // --------------------------------------------------
    // FIND BY ID
    // --------------------------------------------------

    @Test
    void findById_shouldReturnTransaction() {

        Transaction transaction = createTransaction(
                1L,
                "2500.00",
                TransactionType.EXPENSE,
                "Food",
                "Dinner"
        );

        Transaction saved =
                transactionRepository.saveAndFlush(transaction);

        Optional<Transaction> result =
                transactionRepository.findById(saved.getId());

        assertTrue(result.isPresent());

        assertEquals(
                "Food",
                result.get().getCategory()
        );

        assertEquals(
                new BigDecimal("2500.00"),
                result.get().getAmount()
        );
    }


    // --------------------------------------------------
    // FIND BY ID - NOT FOUND
    // --------------------------------------------------

    @Test
    void findById_shouldReturnEmptyWhenTransactionDoesNotExist() {

        Optional<Transaction> result =
                transactionRepository.findById(99999L);

        assertTrue(result.isEmpty());
    }


    // --------------------------------------------------
    // DELETE
    // --------------------------------------------------

    @Test
    void delete_shouldRemoveTransaction() {

        Transaction transaction = createTransaction(
                1L,
                "1000.00",
                TransactionType.EXPENSE,
                "Shopping",
                "Clothes"
        );

        Transaction saved =
                transactionRepository.saveAndFlush(transaction);

        transactionRepository.delete(saved);
        transactionRepository.flush();

        Optional<Transaction> result =
                transactionRepository.findById(saved.getId());

        assertTrue(result.isEmpty());
    }


    // --------------------------------------------------
    // FIND BY USER ID
    // --------------------------------------------------

    @Test
    void findByUserId_shouldReturnUserTransactions() {

        Transaction transaction1 = createTransaction(
                1L,
                "5000.00",
                TransactionType.INCOME,
                "Salary",
                "Monthly salary"
        );

        Transaction transaction2 = createTransaction(
                1L,
                "1500.00",
                TransactionType.EXPENSE,
                "Food",
                "Dinner"
        );

        Transaction transaction3 = createTransaction(
                2L,
                "3000.00",
                TransactionType.INCOME,
                "Salary",
                "Other user"
        );

        transactionRepository.saveAllAndFlush(
                List.of(
                        transaction1,
                        transaction2,
                        transaction3
                )
        );

        Pageable pageable =
                PageRequest.of(0, 2);

        Page<Transaction> result =
                transactionRepository.findByUserId(
                        1L,
                        pageable
                );

        assertEquals(
                2,
                result.getContent().size()
        );

        assertTrue(
                result.stream()
                        .allMatch(transaction ->
                                transaction.getUserId().equals(1L))
        );
    }


    // --------------------------------------------------
    // FIND BY USER ID - EMPTY
    // --------------------------------------------------

    @Test
    void findByUserId_shouldReturnEmptyWhenUserHasNoTransactions() {

        Pageable pageable =
                PageRequest.of(0, 1);

        Page<Transaction> result =
                transactionRepository.findByUserId(
                        999L,
                        pageable
                );

        assertTrue(result.isEmpty());
    }


    // --------------------------------------------------
    // PAGINATION BY USER
    // --------------------------------------------------

    @Test
    void findByUserId_shouldSupportPagination() {

        Transaction transaction1 = createTransaction(
                1L,
                "1000.00",
                TransactionType.EXPENSE,
                "Food",
                "Food 1"
        );

        Transaction transaction2 = createTransaction(
                1L,
                "2000.00",
                TransactionType.EXPENSE,
                "Travel",
                "Travel"
        );

        Transaction transaction3 = createTransaction(
                1L,
                "3000.00",
                TransactionType.INCOME,
                "Salary",
                "Salary"
        );

        transactionRepository.saveAllAndFlush(
                List.of(
                        transaction1,
                        transaction2,
                        transaction3
                )
        );

        Pageable pageable =
                PageRequest.of(0, 2);

        Page<Transaction> result =
                transactionRepository.findByUserId(
                        1L,
                        pageable
                );

        assertEquals(
                2,
                result.getContent().size()
        );

        assertEquals(
                3,
                result.getTotalElements()
        );

        assertEquals(
                0,
                result.getNumber()
        );

        assertEquals(
                2,
                result.getSize()
        );
    }


    // --------------------------------------------------
    // FILTER BY USER + TYPE
    // --------------------------------------------------

    @Test
    void findByUserIdAndType_shouldReturnMatchingTransactions() {

        Transaction income = createTransaction(
                1L,
                "5000.00",
                TransactionType.INCOME,
                "Salary",
                "Monthly salary"
        );

        Transaction expense = createTransaction(
                1L,
                "1500.00",
                TransactionType.EXPENSE,
                "Food",
                "Dinner"
        );

        transactionRepository.saveAllAndFlush(
                List.of(income, expense)
        );

        List<Transaction> result =
                transactionRepository.findByUserIdAndType(
                        1L,
                        TransactionType.EXPENSE
                );

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                TransactionType.EXPENSE,
                result.get(0).getType()
        );

        assertEquals(
                "Food",
                result.get(0).getCategory()
        );
    }


    // --------------------------------------------------
    // FILTER BY USER + TYPE + PAGINATION
    // --------------------------------------------------

    @Test
    void findByUserIdAndType_shouldSupportPagination() {

        Transaction expense1 = createTransaction(
                1L,
                "1000.00",
                TransactionType.EXPENSE,
                "Food",
                "Food 1"
        );

        Transaction expense2 = createTransaction(
                1L,
                "2000.00",
                TransactionType.EXPENSE,
                "Travel",
                "Travel"
        );

        Transaction income = createTransaction(
                1L,
                "5000.00",
                TransactionType.INCOME,
                "Salary",
                "Salary"
        );

        transactionRepository.saveAllAndFlush(
                List.of(
                        expense1,
                        expense2,
                        income
                )
        );

        Pageable pageable =
                PageRequest.of(0, 1);

        Page<Transaction> result =
                transactionRepository.findByUserIdAndType(
                        1L,
                        TransactionType.EXPENSE,
                        pageable
                );

        assertEquals(
                1,
                result.getContent().size()
        );

        assertEquals(
                2,
                result.getTotalElements()
        );

        assertEquals(
                0,
                result.getNumber()
        );

        assertEquals(
                1,
                result.getSize()
        );

        assertEquals(
                TransactionType.EXPENSE,
                result.getContent()
                        .get(0)
                        .getType()
        );
    }


    // --------------------------------------------------
    // SORTING
    // --------------------------------------------------

    @Test
    void findByUserId_shouldSupportSorting() {

        Transaction older = createTransaction(
                1L,
                "1000.00",
                TransactionType.EXPENSE,
                "Food",
                "Older"
        );

        Transaction newer = createTransaction(
                1L,
                "2000.00",
                TransactionType.EXPENSE,
                "Travel",
                "Newer"
        );

        transactionRepository.saveAndFlush(older);
        transactionRepository.saveAndFlush(newer);

        Pageable pageable =
                PageRequest.of(
                        0,
                        10,
                        Sort.by("amount").descending()
                );

        Page<Transaction> result =
                transactionRepository.findByUserId(
                        1L,
                        pageable
                );

        assertEquals(
                2,
                result.getTotalElements()
        );

        assertEquals(
                new BigDecimal("2000.00"),
                result.getContent()
                        .get(0)
                        .getAmount()
        );

        assertEquals(
                new BigDecimal("1000.00"),
                result.getContent()
                        .get(1)
                        .getAmount()
        );
    }


    // --------------------------------------------------
    // HELPER
    // --------------------------------------------------

    private Transaction createTransaction(
            Long userId,
            String amount,
            TransactionType type,
            String category,
            String description) {

        Transaction transaction = new Transaction();

        transaction.setUserId(userId);

        transaction.setAmount(
                new BigDecimal(amount)
        );

        transaction.setType(type);
        transaction.setCategory(category);
        transaction.setDescription(description);

        transaction.setTransactionDate(
                LocalDate.now()
        );

        return transaction;
    }

    @Test
    void findByUserId_shouldReturnOnlyRequestedUsersTransactions() {

        Transaction user1Transaction =
                createTransaction(
                        100L,
                        "1000.00",
                        TransactionType.EXPENSE,
                        "Food",
                        "User 100"
                );

        Transaction user2Transaction =
                createTransaction(
                        200L,
                        "2000.00",
                        TransactionType.EXPENSE,
                        "Travel",
                        "User 200"
                );

        transactionRepository.saveAllAndFlush(
                List.of(
                        user1Transaction,
                        user2Transaction
                )
        );

        Page<Transaction> result =
                transactionRepository.findByUserId(
                        100L,
                        PageRequest.of(0, 10)
                );

        assertEquals(
                1,
                result.getTotalElements()
        );

        assertEquals(
                100L,
                result.getContent()
                        .get(0)
                        .getUserId()
        );
    }

    @Test
    void findByUserIdAndType_shouldFilterByBothFields() {

        Transaction expense =
                createTransaction(
                        100L,
                        "1000.00",
                        TransactionType.EXPENSE,
                        "Food",
                        "Expense"
                );

        Transaction income =
                createTransaction(
                        100L,
                        "5000.00",
                        TransactionType.INCOME,
                        "Salary",
                        "Income"
                );

        Transaction otherUserExpense =
                createTransaction(
                        200L,
                        "2000.00",
                        TransactionType.EXPENSE,
                        "Food",
                        "Other user"
                );

        transactionRepository.saveAllAndFlush(
                List.of(
                        expense,
                        income,
                        otherUserExpense
                )
        );

        List<Transaction> result =
                transactionRepository.findByUserIdAndType(
                        100L,
                        TransactionType.EXPENSE
                );

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                100L,
                result.get(0).getUserId()
        );

        assertEquals(
                TransactionType.EXPENSE,
                result.get(0).getType()
        );
    }


    @Test
    void findByUserIdAndType_shouldReturnOnlyMatchingTransactions() {

        Transaction expense1 = createTransaction(
                1L,
                "1000.00",
                TransactionType.EXPENSE,
                "Food",
                "Food 1"
        );

        Transaction expense2 = createTransaction(
                1L,
                "2000.00",
                TransactionType.EXPENSE,
                "Travel",
                "Travel"
        );

        Transaction income = createTransaction(
                1L,
                "5000.00",
                TransactionType.INCOME,
                "Salary",
                "Salary"
        );

        transactionRepository.saveAllAndFlush(
                List.of(
                        expense1,
                        expense2,
                        income
                )
        );

        List<Transaction> result =
                transactionRepository.findByUserIdAndType(
                        1L,
                        TransactionType.EXPENSE
                );

        assertEquals(2, result.size());

        assertTrue(
                result.stream()
                        .allMatch(
                                transaction ->
                                        transaction.getType()
                                                == TransactionType.EXPENSE
                        )
        );
    }
}