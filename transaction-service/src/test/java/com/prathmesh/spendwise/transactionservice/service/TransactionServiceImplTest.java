package com.prathmesh.spendwise.transactionservice.service;

import com.prathmesh.spendwise.transactionservice.client.UserServiceClient;
import com.prathmesh.spendwise.transactionservice.dto.request.TransactionRequest;
import com.prathmesh.spendwise.transactionservice.dto.response.TransactionResponse;
import com.prathmesh.spendwise.transactionservice.entity.Transaction;
import com.prathmesh.spendwise.transactionservice.entity.TransactionType;
import com.prathmesh.spendwise.transactionservice.exception.TransactionNotFoundException;
import com.prathmesh.spendwise.transactionservice.repository.TransactionRepository;
import com.prathmesh.spendwise.transactionservice.service.impl.TransactionServiceImpl;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private TransactionRequest request;
    private Transaction transaction;
    private TransactionResponse response;


    @BeforeEach
    void setUp() {

        request = createRequest();

        transaction = createTransaction();

        response = createResponse();
    }


    // =========================================================
    // CREATE
    // =========================================================

    @Test
    void createTransaction_shouldCreateSuccessfully() {

        when(userServiceClient.getUserById(100L))
                .thenReturn(null);

        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(transaction);

        TransactionResponse result =
                transactionService.createTransaction(request);

        assertNotNull(result);

        assertEquals(
                1L,
                result.getId()
        );

        assertEquals(
                100L,
                result.getUserId()
        );

        assertEquals(
                new BigDecimal("5000.00"),
                result.getAmount()
        );

        assertEquals(
                TransactionType.EXPENSE,
                result.getType()
        );

        assertEquals(
                "Food",
                result.getCategory()
        );

        verify(userServiceClient)
                .getUserById(100L);

        verify(transactionRepository)
                .save(any(Transaction.class));

        assertEquals(
                1.0,
                meterRegistry
                        .counter("spendwise.transactions.created")
                        .count()
        );
    }


    @Test
    void createTransaction_shouldValidateUserBeforeSaving() {

        when(userServiceClient.getUserById(100L))
                .thenThrow(
                        new RuntimeException(
                                "User not found"
                        )
                );

        assertThrows(
                RuntimeException.class,
                () -> transactionService
                        .createTransaction(request)
        );

        verify(userServiceClient)
                .getUserById(100L);

        verify(transactionRepository, never())
                .save(any(Transaction.class));
    }


    // =========================================================
    // GET BY USER
    // =========================================================

    @Test
    void getTransactionsByUser_shouldReturnTransactions() {

        Pageable pageable =
                PageRequest.of(
                        0,
                        10,
                        Sort.by("transactionDate")
                                .descending()
                );

        Page<Transaction> transactionPage =
                new PageImpl<>(
                        List.of(transaction),
                        pageable,
                        1
                );

        when(transactionRepository.findByUserId(
                100L,
                pageable
        )).thenReturn(transactionPage);

        Page<TransactionResponse> result =
                transactionService.getTransactionsByUser(
                        100L,
                        pageable
                );

        assertNotNull(result);

        assertEquals(
                1,
                result.getTotalElements()
        );

        assertEquals(
                1,
                result.getContent().size()
        );

        assertEquals(
                100L,
                result.getContent()
                        .get(0)
                        .getUserId()
        );

        verify(transactionRepository)
                .findByUserId(
                        100L,
                        pageable
                );
    }


    @Test
    void getTransactionsByUser_shouldReturnEmptyPage() {

        Pageable pageable =
                PageRequest.of(0, 10);

        Page<Transaction> emptyPage =
                new PageImpl<>(List.of());

        when(transactionRepository.findByUserId(
                999L,
                pageable
        )).thenReturn(emptyPage);

        Page<TransactionResponse> result =
                transactionService.getTransactionsByUser(
                        999L,
                        pageable
                );

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(transactionRepository)
                .findByUserId(
                        999L,
                        pageable
                );
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Test
    void getTransactionById_shouldReturnTransaction() {

        when(transactionRepository.findById(1L))
                .thenReturn(
                        Optional.of(transaction)
                );

        TransactionResponse result =
                transactionService
                        .getTransactionById(1L);

        assertNotNull(result);

        assertEquals(
                1L,
                result.getId()
        );

        assertEquals(
                100L,
                result.getUserId()
        );

        assertEquals(
                new BigDecimal("5000.00"),
                result.getAmount()
        );

        assertEquals(
                TransactionType.EXPENSE,
                result.getType()
        );

        assertEquals(
                "Food",
                result.getCategory()
        );

        assertEquals(
                "Dinner",
                result.getDescription()
        );

        verify(transactionRepository)
                .findById(1L);
    }


    @Test
    void getTransactionById_shouldThrowWhenNotFound() {

        when(transactionRepository.findById(99L))
                .thenReturn(Optional.empty());

        TransactionNotFoundException exception =
                assertThrows(
                        TransactionNotFoundException.class,
                        () -> transactionService
                                .getTransactionById(99L)
                );

        assertEquals(
                "Transaction not found with Id : 99",
                exception.getMessage()
        );

        verify(transactionRepository)
                .findById(99L);
    }


    // =========================================================
    // UPDATE
    // =========================================================

    @Test
    void updateTransaction_shouldUpdateSuccessfully() {

        request.setAmount(
                new BigDecimal("7500.00")
        );

        request.setCategory("Travel");

        request.setDescription(
                "Flight ticket"
        );

        Transaction updated =
                createTransaction();

        updated.setAmount(
                new BigDecimal("7500.00")
        );

        updated.setCategory("Travel");

        updated.setDescription(
                "Flight ticket"
        );

        when(transactionRepository.findById(1L))
                .thenReturn(
                        Optional.of(transaction)
                );

        when(transactionRepository.save(transaction))
                .thenReturn(updated);

        TransactionResponse result =
                transactionService.updateTransaction(
                        1L,
                        request
                );

        assertNotNull(result);

        assertEquals(
                new BigDecimal("7500.00"),
                result.getAmount()
        );

        assertEquals(
                "Travel",
                result.getCategory()
        );

        assertEquals(
                "Flight ticket",
                result.getDescription()
        );

        assertEquals(
                100L,
                transaction.getUserId()
        );

        assertEquals(
                new BigDecimal("7500.00"),
                transaction.getAmount()
        );

        assertEquals(
                "Travel",
                transaction.getCategory()
        );

        verify(transactionRepository)
                .findById(1L);

        verify(transactionRepository)
                .save(transaction);

        assertEquals(
                1.0,
                meterRegistry
                        .counter("spendwise.transactions.updated")
                        .count()
        );
    }


    @Test
    void updateTransaction_shouldThrowWhenNotFound() {

        when(transactionRepository.findById(99L))
                .thenReturn(Optional.empty());

        TransactionNotFoundException exception =
                assertThrows(
                        TransactionNotFoundException.class,
                        () -> transactionService
                                .updateTransaction(
                                        99L,
                                        request
                                )
                );

        assertEquals(
                "Transaction not found with Id : 99",
                exception.getMessage()
        );

        verify(transactionRepository)
                .findById(99L);

        verify(transactionRepository, never())
                .save(any(Transaction.class));
    }


    // =========================================================
    // DELETE
    // =========================================================

    @Test
    void deleteTransaction_shouldDeleteSuccessfully() {

        when(transactionRepository.existsById(1L))
                .thenReturn(true);

        transactionService.deleteTransaction(1L);

        verify(transactionRepository)
                .existsById(1L);

        verify(transactionRepository)
                .deleteById(1L);

        assertEquals(
                1.0,
                meterRegistry
                        .counter("spendwise.transactions.deleted")
                        .count()
        );
    }


    @Test
    void deleteTransaction_shouldThrowWhenNotFound() {

        when(transactionRepository.existsById(99L))
                .thenReturn(false);

        TransactionNotFoundException exception =
                assertThrows(
                        TransactionNotFoundException.class,
                        () -> transactionService
                                .deleteTransaction(99L)
                );

        assertEquals(
                "Transaction not found with Id : 99",
                exception.getMessage()
        );

        verify(transactionRepository)
                .existsById(99L);

        verify(transactionRepository, never())
                .deleteById(99L);
    }


    // =========================================================
    // GET BY USER + TYPE
    // =========================================================

    @Test
    void getTransactionsByUserAndType_shouldReturnMatchingTransactions() {

        Transaction expense =
                createTransaction();

        expense.setType(
                TransactionType.EXPENSE
        );

        when(
                transactionRepository
                        .findByUserIdAndType(
                                100L,
                                TransactionType.EXPENSE
                        )
        ).thenReturn(
                List.of(expense)
        );

        List<TransactionResponse> result =
                transactionService
                        .getTransactionsByUserAndType(
                                100L,
                                TransactionType.EXPENSE
                        );

        assertNotNull(result);

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                TransactionType.EXPENSE,
                result.get(0).getType()
        );

        assertEquals(
                100L,
                result.get(0).getUserId()
        );

        verify(
                transactionRepository
        ).findByUserIdAndType(
                100L,
                TransactionType.EXPENSE
        );
    }


    @Test
    void getTransactionsByUserAndType_shouldReturnEmptyList() {

        when(
                transactionRepository
                        .findByUserIdAndType(
                                999L,
                                TransactionType.EXPENSE
                        )
        ).thenReturn(List.of());

        List<TransactionResponse> result =
                transactionService
                        .getTransactionsByUserAndType(
                                999L,
                                TransactionType.EXPENSE
                        );

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(
                transactionRepository
        ).findByUserIdAndType(
                999L,
                TransactionType.EXPENSE
        );
    }


    // =========================================================
    // GET ALL TRANSACTIONS
    // =========================================================

    @Test
    void getTransactions_shouldReturnTransactions() {

        Pageable pageable =
                PageRequest.of(
                        0,
                        10,
                        Sort.by("amount").descending()
                );

        Page<Transaction> page =
                new PageImpl<>(
                        List.of(transaction),
                        pageable,
                        1
                );

        when(transactionRepository.findAll(pageable))
                .thenReturn(page);

        Page<TransactionResponse> result =
                transactionService
                        .getTransactions(pageable);

        assertNotNull(result);

        assertEquals(
                1,
                result.getTotalElements()
        );

        assertEquals(
                new BigDecimal("5000.00"),
                result.getContent()
                        .get(0)
                        .getAmount()
        );

        verify(transactionRepository)
                .findAll(pageable);
    }


    // =========================================================
    // GET BY TYPE
    // =========================================================

    @Test
    void getTransactionsByType_shouldReturnMatchingTransactions() {

        when(
                transactionRepository
                        .findByType(
                                TransactionType.EXPENSE
                        )
        ).thenReturn(
                List.of(transaction)
        );

        List<TransactionResponse> result =
                transactionService
                        .getTransactionsByType(
                                TransactionType.EXPENSE
                        );

        assertNotNull(result);

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                TransactionType.EXPENSE,
                result.get(0).getType()
        );

        verify(
                transactionRepository
        ).findByType(
                TransactionType.EXPENSE
        );
    }


    @Test
    void getTransactionsByType_shouldReturnEmptyList() {

        when(
                transactionRepository
                        .findByType(
                                TransactionType.INCOME
                        )
        ).thenReturn(List.of());

        List<TransactionResponse> result =
                transactionService
                        .getTransactionsByType(
                                TransactionType.INCOME
                        );

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(
                transactionRepository
        ).findByType(
                TransactionType.INCOME
        );
    }


    // =========================================================
    // HELPERS
    // =========================================================

    private TransactionRequest createRequest() {

        TransactionRequest request =
                new TransactionRequest();

        request.setUserId(100L);

        request.setAmount(
                new BigDecimal("5000.00")
        );

        request.setType(
                TransactionType.EXPENSE
        );

        request.setCategory("Food");

        request.setDescription("Dinner");

        request.setTransactionDate(
                LocalDate.of(2026, 8, 20)
        );

        return request;
    }


    private Transaction createTransaction() {

        Transaction transaction =
                new Transaction();

        transaction.setId(1L);

        transaction.setUserId(100L);

        transaction.setAmount(
                new BigDecimal("5000.00")
        );

        transaction.setType(
                TransactionType.EXPENSE
        );

        transaction.setCategory("Food");

        transaction.setDescription("Dinner");

        transaction.setTransactionDate(
                LocalDate.of(2026, 8, 20)
        );

        return transaction;
    }


    private TransactionResponse createResponse() {

        TransactionResponse response =
                new TransactionResponse();

        response.setId(1L);

        response.setUserId(100L);

        response.setAmount(
                new BigDecimal("5000.00")
        );

        response.setType(
                TransactionType.EXPENSE
        );

        response.setCategory("Food");

        response.setDescription("Dinner");

        response.setTransactionDate(
                LocalDate.of(2026, 8, 20)
        );

        return response;
    }
}