package com.prathmesh.spendwise.transactionservice.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.prathmesh.spendwise.transactionservice.config.SecurityConfig;
import com.prathmesh.spendwise.transactionservice.dto.request.TransactionRequest;
import com.prathmesh.spendwise.transactionservice.dto.response.TransactionResponse;
import com.prathmesh.spendwise.transactionservice.entity.TransactionType;
import com.prathmesh.spendwise.transactionservice.exception.TransactionNotFoundException;
import com.prathmesh.spendwise.transactionservice.service.TransactionService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;


    // --------------------------------------------------
    // CREATE
    // --------------------------------------------------

    @Test
    void createTransaction_shouldReturn201() throws Exception {

        TransactionRequest request =
                createRequest();

        TransactionResponse response =
                createResponse();

        when(
                transactionService.createTransaction(
                        any(TransactionRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(100))
                .andExpect(
                        jsonPath("$.amount")
                                .value(5000.00)
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("EXPENSE")
                )
                .andExpect(
                        jsonPath("$.category")
                                .value("Food")
                );

        verify(transactionService)
                .createTransaction(
                        any(TransactionRequest.class)
                );
    }


    // --------------------------------------------------
    // CREATE - VALIDATION
    // --------------------------------------------------

    @Test
    void createTransaction_shouldReturn400WhenRequestIsInvalid()
            throws Exception {

        TransactionRequest request =
                new TransactionRequest();

        request.setUserId(null);
        request.setAmount(
                new BigDecimal("0")
        );
        request.setType(null);
        request.setCategory("");
        request.setDescription("Dinner");
        request.setTransactionDate(null);

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                transactionService,
                never()
        ).createTransaction(
                any(TransactionRequest.class)
        );
    }


    // --------------------------------------------------
    // GET BY USER
    // --------------------------------------------------

    @Test
    void getTransactions_shouldReturn200() throws Exception {

        TransactionResponse response =
                createResponse();

        PageImpl<TransactionResponse> page =
                new PageImpl<>(
                        List.of(response),
                        PageRequest.of(0, 10),
                        1
                );

        when(
                transactionService.getTransactionsByUser(
                        eq(100L),
                        any()
                )
        ).thenReturn(page);

        mockMvc.perform(
                        get("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .param(
                                        "userId",
                                        "100"
                                )
                                .param(
                                        "page",
                                        "0"
                                )
                                .param(
                                        "size",
                                        "10"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].userId")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(1)
                );

        verify(transactionService)
                .getTransactionsByUser(
                        eq(100L),
                        any()
                );
    }


    // --------------------------------------------------
    // GET BY USER - PAGINATION + SORTING
    // --------------------------------------------------

    @Test
    void getTransactions_shouldSupportPaginationAndSorting()
            throws Exception {

        TransactionResponse response =
                createResponse();

        PageImpl<TransactionResponse> page =
                new PageImpl<>(
                        List.of(response),
                        PageRequest.of(
                                1,
                                5
                        ),
                        6
                );

        when(
                transactionService.getTransactionsByUser(
                        eq(100L),
                        any()
                )
        ).thenReturn(page);

        mockMvc.perform(
                        get("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .param(
                                        "userId",
                                        "100"
                                )
                                .param(
                                        "page",
                                        "1"
                                )
                                .param(
                                        "size",
                                        "5"
                                )
                                .param(
                                        "sort",
                                        "amount,desc"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.number")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(5)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(6)
                );

        verify(transactionService)
                .getTransactionsByUser(
                        eq(100L),
                        any()
                );
    }


    // --------------------------------------------------
    // GET BY ID
    // --------------------------------------------------

    @Test
    void getTransactionById_shouldReturn200()
            throws Exception {

        TransactionResponse response =
                createResponse();

        when(
                transactionService.getTransactionById(1L)
        ).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/transactions/1")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.userId")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.category")
                                .value("Food")
                );

        verify(transactionService)
                .getTransactionById(1L);
    }


    // --------------------------------------------------
    // GET BY ID - NOT FOUND
    // --------------------------------------------------

    @Test
    void getTransactionById_shouldReturn404WhenNotFound()
            throws Exception {

        when(
                transactionService.getTransactionById(99L)
        ).thenThrow(
                new TransactionNotFoundException(
                        "Transaction not found with Id : 99"
                )
        );

        mockMvc.perform(
                        get("/api/v1/transactions/99")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                )
                .andExpect(status().isNotFound());

        verify(transactionService)
                .getTransactionById(99L);
    }


    // --------------------------------------------------
    // UPDATE
    // --------------------------------------------------

    @Test
    void updateTransaction_shouldReturn200()
            throws Exception {

        TransactionRequest request =
                createRequest();

        TransactionResponse response =
                createResponse();

        response.setCategory("Travel");
        response.setAmount(
                new BigDecimal("7500.00")
        );

        when(
                transactionService.updateTransaction(
                        eq(1L),
                        any(TransactionRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        put("/api/v1/transactions/1")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.category")
                                .value("Travel")
                )
                .andExpect(
                        jsonPath("$.amount")
                                .value(7500.00)
                );

        verify(transactionService)
                .updateTransaction(
                        eq(1L),
                        any(TransactionRequest.class)
                );
    }


    // --------------------------------------------------
    // DELETE
    // --------------------------------------------------

    @Test
    void deleteTransaction_shouldReturn204()
            throws Exception {

        doNothing()
                .when(transactionService)
                .deleteTransaction(1L);

        mockMvc.perform(
                        delete("/api/v1/transactions/1")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                )
                .andExpect(status().isNoContent());

        verify(transactionService)
                .deleteTransaction(1L);
    }


    // --------------------------------------------------
    // FILTER BY TYPE
    // --------------------------------------------------

    @Test
    void getTransactionsByType_shouldReturn200()
            throws Exception {

        TransactionResponse response =
                createResponse();

        when(
                transactionService
                        .getTransactionsByUserAndType(
                                100L,
                                TransactionType.EXPENSE
                        )
        ).thenReturn(
                List.of(response)
        );

        mockMvc.perform(
                        get("/api/v1/transactions/type")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .param(
                                        "userId",
                                        "100"
                                )
                                .param(
                                        "type",
                                        "EXPENSE"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].type")
                                .value("EXPENSE")
                );

        verify(transactionService)
                .getTransactionsByUserAndType(
                        100L,
                        TransactionType.EXPENSE
                );
    }


    // --------------------------------------------------
    // FILTER BY TYPE - EMPTY
    // --------------------------------------------------

    @Test
    void getTransactionsByType_shouldReturnEmptyList()
            throws Exception {

        when(
                transactionService
                        .getTransactionsByUserAndType(
                                100L,
                                TransactionType.INCOME
                        )
        ).thenReturn(List.of());

        mockMvc.perform(
                        get("/api/v1/transactions/type")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .param(
                                        "userId",
                                        "100"
                                )
                                .param(
                                        "type",
                                        "INCOME"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(0)
                );

        verify(transactionService)
                .getTransactionsByUserAndType(
                        100L,
                        TransactionType.INCOME
                );
    }

    @Test
    void createTransaction_shouldReturn400WhenUserIdIsNegative()
            throws Exception {

        TransactionRequest request = createRequest();
        request.setUserId(-1L);

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                transactionService,
                never()
        ).createTransaction(any(TransactionRequest.class));
    }

    @Test
    void createTransaction_shouldReturn400WhenAmountIsNegative()
            throws Exception {

        TransactionRequest request = createRequest();
        request.setAmount(new BigDecimal("-100.00"));

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                transactionService,
                never()
        ).createTransaction(any(TransactionRequest.class));
    }

    @Test
    void createTransaction_shouldReturn400WhenTypeIsMissing()
            throws Exception {

        TransactionRequest request = createRequest();
        request.setType(null);

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                transactionService,
                never()
        ).createTransaction(any(TransactionRequest.class));
    }

    @Test
    void createTransaction_shouldReturn400WhenCategoryIsMissing()
            throws Exception {

        TransactionRequest request = createRequest();
        request.setCategory(null);

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                transactionService,
                never()
        ).createTransaction(any(TransactionRequest.class));
    }

    @Test
    void createTransaction_shouldReturn400WhenCategoryIsBlank()
            throws Exception {

        TransactionRequest request = createRequest();
        request.setCategory("   ");

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                transactionService,
                never()
        ).createTransaction(any(TransactionRequest.class));
    }

    @Test
    void createTransaction_shouldReturn400WhenTransactionDateIsMissing()
            throws Exception {

        TransactionRequest request = createRequest();
        request.setTransactionDate(null);

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                transactionService,
                never()
        ).createTransaction(any(TransactionRequest.class));
    }

    @Test
    void updateTransaction_shouldReturn400WhenRequestIsInvalid()
            throws Exception {

        TransactionRequest request = createRequest();

        request.setUserId(-1L);
        request.setAmount(BigDecimal.ZERO);
        request.setType(null);
        request.setCategory("");
        request.setTransactionDate(null);

        mockMvc.perform(
                        put("/api/v1/transactions/1")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                transactionService,
                never()
        ).updateTransaction(
                eq(1L),
                any(TransactionRequest.class)
        );
    }

    @Test
    void updateTransaction_shouldReturn404WhenNotFound()
            throws Exception {

        TransactionRequest request = createRequest();

        when(
                transactionService.updateTransaction(
                        eq(99L),
                        any(TransactionRequest.class)
                )
        ).thenThrow(
                new TransactionNotFoundException(
                        "Transaction not found with Id : 99"
                )
        );

        mockMvc.perform(
                        put("/api/v1/transactions/99")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status").value(404)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Transaction not found with Id : 99"
                                )
                );

        verify(
                transactionService
        ).updateTransaction(
                eq(99L),
                any(TransactionRequest.class)
        );
    }

    @Test
    void deleteTransaction_shouldReturn404WhenNotFound()
            throws Exception {

        doThrow(
                new TransactionNotFoundException(
                        "Transaction not found with Id : 99"
                )
        )
                .when(transactionService)
                .deleteTransaction(99L);

        mockMvc.perform(
                        delete("/api/v1/transactions/99")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status").value(404)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Transaction not found with Id : 99"
                                )
                );

        verify(transactionService)
                .deleteTransaction(99L);
    }




    // --------------------------------------------------
    // HELPERS
    // --------------------------------------------------

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

    @Test
    void createTransaction_shouldReturn400WhenRequestIsMalformed()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "userId": 100,
                                  "amount": 5000.00,
                                  "type": "EXPENSE",
                                  "category": "Food",
                                  "transactionDate":
                            """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status").value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Malformed request")
                );

        verify(
                transactionService,
                never()
        ).createTransaction(
                any(TransactionRequest.class)
        );
    }

    @Test
    void createTransaction_shouldReturn500WhenUnexpectedExceptionOccurs()
            throws Exception {

        when(
                transactionService.createTransaction(
                        any(TransactionRequest.class)
                )
        ).thenThrow(
                new RuntimeException("Database failure")
        );

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .with(SecurityMockMvcRequestPostProcessors.jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                createRequest()
                                        )
                                )
                )
                .andExpect(status().isInternalServerError())
                .andExpect(
                        jsonPath("$.status").value(500)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("An unexpected error occurred")
                );
    }
}
