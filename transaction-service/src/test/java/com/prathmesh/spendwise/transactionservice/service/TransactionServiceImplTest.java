package com.prathmesh.spendwise.transactionservice.service;

import com.prathmesh.spendwise.transactionservice.dto.request.TransactionRequest;
import com.prathmesh.spendwise.transactionservice.dto.response.TransactionResponse;
import com.prathmesh.spendwise.transactionservice.entity.Transaction;
import com.prathmesh.spendwise.transactionservice.entity.TransactionType;
import com.prathmesh.spendwise.transactionservice.exception.TransactionNotFoundException;
import com.prathmesh.spendwise.transactionservice.repository.TransactionRepository;
import com.prathmesh.spendwise.transactionservice.service.impl.TransactionServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceImplTest {


        @Mock
        private TransactionRepository transactionRepository;

        @InjectMocks
        private TransactionServiceImpl transactionService;

        private Transaction transaction;
        private TransactionRequest request;
        private TransactionResponse response;

        @BeforeEach
        void setUp() {

            transaction = new Transaction();

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

            request = new TransactionRequest();

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

            response = new TransactionResponse();

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
            response.setCreatedAt(
                    transaction.getCreatedAt()
            );
            response.setUpdatedAt(
                    transaction.getUpdatedAt()
            );
        }


        // --------------------------------------------------
        // CREATE
        // --------------------------------------------------

        @Test
        void createTransaction_shouldCreateSuccessfully() {

            when(transactionRepository.save(any(Transaction.class)))
                    .thenReturn(transaction);

            TransactionResponse result =
                    transactionService.createTransaction(request);

            assertNotNull(result);

            assertEquals(1L, result.getId());
            assertEquals(100L, result.getUserId());

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

            verify(transactionRepository)
                    .save(any(Transaction.class));
        }


        // --------------------------------------------------
        // GET BY USER
        // --------------------------------------------------

        @Test
        void getTransactionsByUser_shouldReturnTransactions() {

            Pageable pageable =
                    PageRequest.of(0, 10);

            Page<Transaction> page =
                    new PageImpl<>(
                            List.of(transaction),
                            pageable,
                            1
                    );

            when(
                    transactionRepository.findByUserId(
                            100L,
                            pageable
                    )
            ).thenReturn(page);

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
                    .findByUserId(100L, pageable);
        }


        // --------------------------------------------------
        // GET BY USER - EMPTY
        // --------------------------------------------------

        @Test
        void getTransactionsByUser_shouldReturnEmptyPage() {

            Pageable pageable =
                    PageRequest.of(0, 10);

            Page<Transaction> emptyPage =
                    new PageImpl<>(
                            List.of(),
                            pageable,
                            0
                    );

            when(
                    transactionRepository.findByUserId(
                            999L,
                            pageable
                    )
            ).thenReturn(emptyPage);

            Page<TransactionResponse> result =
                    transactionService.getTransactionsByUser(
                            999L,
                            pageable
                    );

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEquals(0, result.getTotalElements());

            verify(transactionRepository)
                    .findByUserId(999L, pageable);
        }


        // --------------------------------------------------
        // GET BY ID - SUCCESS
        // --------------------------------------------------

        @Test
        void getTransactionById_shouldReturnTransaction() {

            when(
                    transactionRepository.findById(1L)
            ).thenReturn(Optional.of(transaction));

            TransactionResponse result =
                    transactionService.getTransactionById(1L);

            assertNotNull(result);

            assertEquals(1L, result.getId());
            assertEquals(100L, result.getUserId());

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

            verify(transactionRepository)
                    .findById(1L);
        }


        // --------------------------------------------------
        // GET BY ID - NOT FOUND
        // --------------------------------------------------

        @Test
        void getTransactionById_shouldThrowExceptionWhenNotFound() {

            when(
                    transactionRepository.findById(99L)
            ).thenReturn(Optional.empty());

            assertThrows(
                    TransactionNotFoundException.class,
                    () ->
                            transactionService.getTransactionById(99L)
            );

            verify(transactionRepository)
                    .findById(99L);
        }


        // --------------------------------------------------
        // UPDATE - SUCCESS
        // --------------------------------------------------

        @Test
        void updateTransaction_shouldUpdateSuccessfully() {

            TransactionRequest updateRequest =
                    new TransactionRequest();

            updateRequest.setUserId(100L);
            updateRequest.setAmount(
                    new BigDecimal("7500.00")
            );
            updateRequest.setType(
                    TransactionType.EXPENSE
            );
            updateRequest.setCategory("Travel");
            updateRequest.setDescription("Flight ticket");
            updateRequest.setTransactionDate(
                    LocalDate.of(2026, 8, 21)
            );

            when(
                    transactionRepository.findById(1L)
            ).thenReturn(Optional.of(transaction));

            when(
                    transactionRepository.save(transaction)
            ).thenReturn(transaction);

            TransactionResponse result =
                    transactionService.updateTransaction(
                            1L,
                            updateRequest
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

            verify(transactionRepository)
                    .findById(1L);

            verify(transactionRepository)
                    .save(transaction);
        }


        // --------------------------------------------------
        // UPDATE - NOT FOUND
        // --------------------------------------------------

        @Test
        void updateTransaction_shouldThrowExceptionWhenNotFound() {

            when(
                    transactionRepository.findById(99L)
            ).thenReturn(Optional.empty());

            assertThrows(
                    TransactionNotFoundException.class,
                    () ->
                            transactionService.updateTransaction(
                                    99L,
                                    request
                            )
            );

            verify(transactionRepository)
                    .findById(99L);

            verify(transactionRepository, never())
                    .save(any(Transaction.class));
        }


        // --------------------------------------------------
        // DELETE - SUCCESS
        // --------------------------------------------------

        @Test
        void deleteTransaction_shouldDeleteSuccessfully() {

            when(
                    transactionRepository.existsById(1L)
            ).thenReturn(true);

            transactionService.deleteTransaction(1L);

            verify(transactionRepository)
                    .existsById(1L);

            verify(transactionRepository)
                    .deleteById(1L);
        }


        // --------------------------------------------------
        // DELETE - NOT FOUND
        // --------------------------------------------------

        @Test
        void deleteTransaction_shouldThrowExceptionWhenNotFound() {

            when(
                    transactionRepository.existsById(99L)
            ).thenReturn(false);

            assertThrows(
                    TransactionNotFoundException.class,
                    () ->
                            transactionService.deleteTransaction(99L)
            );

            verify(transactionRepository)
                    .existsById(99L);

            verify(transactionRepository, never())
                    .deleteById(99L);
        }


        // --------------------------------------------------
        // FILTER BY USER + TYPE
        // --------------------------------------------------

        @Test
        void getTransactionsByUserAndType_shouldReturnMatchingTransactions() {

            Transaction expense2 = createTransaction(
                    100L,
                    "2000.00",
                    TransactionType.EXPENSE,
                    "Shopping",
                    "Clothes"
            );

            when(
                    transactionRepository.findByUserIdAndType(
                            100L,
                            TransactionType.EXPENSE
                    )
            ).thenReturn(
                    List.of(transaction, expense2)
            );

            List<TransactionResponse> result =
                    transactionService.getTransactionsByUserAndType(
                            100L,
                            TransactionType.EXPENSE
                    );

            assertNotNull(result);

            assertEquals(2, result.size());

            assertTrue(
                    result.stream()
                            .allMatch(
                                    item ->
                                            item.getUserId().equals(100L)
                                                    &&
                                                    item.getType()
                                                            == TransactionType.EXPENSE
                            )
            );

            verify(transactionRepository)
                    .findByUserIdAndType(
                            100L,
                            TransactionType.EXPENSE
                    );
        }


        // --------------------------------------------------
        // FILTER BY USER + TYPE - EMPTY
        // --------------------------------------------------

        @Test
        void getTransactionsByUserAndType_shouldReturnEmptyList() {

            when(
                    transactionRepository.findByUserIdAndType(
                            100L,
                            TransactionType.INCOME
                    )
            ).thenReturn(List.of());

            List<TransactionResponse> result =
                    transactionService.getTransactionsByUserAndType(
                            100L,
                            TransactionType.INCOME
                    );

            assertNotNull(result);
            assertTrue(result.isEmpty());

            verify(transactionRepository)
                    .findByUserIdAndType(
                            100L,
                            TransactionType.INCOME
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

            Transaction transaction =
                    new Transaction();

            transaction.setUserId(userId);

            transaction.setAmount(
                    new BigDecimal(amount)
            );

            transaction.setType(type);

            transaction.setCategory(category);

            transaction.setDescription(description);

            transaction.setTransactionDate(
                    LocalDate.of(2026, 8, 20)
            );

            return transaction;
        }


    @Test
    void getTransactionById_shouldPropagateUnexpectedRepositoryException() {

        when(transactionRepository.findById(1L))
                .thenThrow(
                        new RuntimeException("Database connection failed")
                );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> transactionService.getTransactionById(1L)
                );

        assertEquals(
                "Database connection failed",
                exception.getMessage()
        );

        verify(transactionRepository)
                .findById(1L);

    }

    @Test
    void createTransaction_shouldPropagateUnexpectedRepositoryException() {

        doThrow(
                new RuntimeException("Database unavailable")
        )
                .when(transactionRepository)
                .save(any(Transaction.class));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> transactionService.createTransaction(request)
                );

        assertEquals(
                "Database unavailable",
                exception.getMessage()
        );

        verify(transactionRepository)
                .save(any(Transaction.class));
    }

    @Test
    void deleteTransaction_shouldPropagateUnexpectedRepositoryException() {

        when(transactionRepository.existsById(1L))
                .thenThrow(
                        new RuntimeException("Database unavailable")
                );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> transactionService.deleteTransaction(1L)
                );

        assertEquals(
                "Database unavailable",
                exception.getMessage()
        );

        verify(transactionRepository)
                .existsById(1L);

        verify(transactionRepository, never())
                .deleteById(1L);
    }
}
