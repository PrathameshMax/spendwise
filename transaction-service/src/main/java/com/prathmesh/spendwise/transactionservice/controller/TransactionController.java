package com.prathmesh.spendwise.transactionservice.controller;

import com.prathmesh.spendwise.transactionservice.dto.request.TransactionRequest;
import com.prathmesh.spendwise.transactionservice.dto.response.TransactionResponse;
import com.prathmesh.spendwise.transactionservice.entity.TransactionType;
import com.prathmesh.spendwise.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService) {

        this.transactionService = transactionService;
    }


    // ==================================================
    // CREATE
    // POST /api/v1/transactions
    // ==================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(
            @Valid @RequestBody TransactionRequest request) {

        return transactionService.createTransaction(request);
    }


    // ==================================================
    // GET BY USER
    //
    // GET /api/v1/transactions?userId=100
    //
    // Supports pagination + sorting
    // ==================================================

    @GetMapping
    public Page<TransactionResponse> getTransactions(
            @RequestParam Long userId,
            Pageable pageable) {

        return transactionService.getTransactionsByUser(
                userId,
                pageable
        );
    }


    // ==================================================
    // GET BY ID
    //
    // GET /api/v1/transactions/{id}
    // ==================================================

    @GetMapping("/{id}")
    public TransactionResponse getTransactionById(
            @PathVariable Long id) {

        return transactionService.getTransactionById(id);
    }


    // ==================================================
    // GET BY USER + TYPE
    //
    // GET /api/v1/transactions/type
    //     ?userId=100&type=EXPENSE
    // ==================================================

    @GetMapping("/type")
    public List<TransactionResponse> getTransactionsByType(
            @RequestParam Long userId,
            @RequestParam TransactionType type) {

        return transactionService.getTransactionsByUserAndType(
                userId,
                type
        );
    }


    // ==================================================
    // GET BY USER + TYPE
    //
    // GET /api/v1/transactions/user/{userId}/type/{type}
    // ==================================================

    @GetMapping("/user/{userId}/type/{type}")
    public List<TransactionResponse> getTransactionsByUserAndType(
            @PathVariable Long userId,
            @PathVariable TransactionType type) {

        return transactionService.getTransactionsByUserAndType(
                userId,
                type
        );
    }


    // ==================================================
    // UPDATE
    //
    // PUT /api/v1/transactions/{id}
    // ==================================================

    @PutMapping("/{id}")
    public TransactionResponse updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request) {

        return transactionService.updateTransaction(
                id,
                request
        );
    }


    // ==================================================
    // DELETE
    //
    // DELETE /api/v1/transactions/{id}
    // ==================================================

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(
            @PathVariable Long id) {

        transactionService.deleteTransaction(id);
    }
}