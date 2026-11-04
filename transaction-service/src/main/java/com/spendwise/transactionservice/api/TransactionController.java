package com.spendwise.transactionservice.api;

import com.spendwise.common.security.CurrentUser;
import com.spendwise.transactionservice.domain.TransactionType;
import com.spendwise.transactionservice.service.StatementMerchantEnricher;
import com.spendwise.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Two levels of authorization:
 * <ol>
 *   <li>Role + scope — {@code @PreAuthorize}: a CUSTOMER token carrying
 *       {@code transactions:read} / {@code transactions:write}. Valid token without
 *       them → 403.</li>
 *   <li>Ownership — the user id comes only from the token ({@link CurrentUser}), and
 *       every query is scoped by it. Asking for someone else's transaction id returns
 *       404, exactly as if it did not exist (IDOR / BOLA protection).</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final StatementMerchantEnricher statementMerchantEnricher;

    public TransactionController(TransactionService transactionService,
                                 StatementMerchantEnricher statementMerchantEnricher) {
        this.transactionService = transactionService;
        this.statementMerchantEnricher = statementMerchantEnricher;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('SCOPE_transactions:write')")
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.create(CurrentUser.require().userId(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('SCOPE_transactions:read')")
    public ResponseEntity<TransactionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getOwned(CurrentUser.require().userId(), id));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('SCOPE_transactions:read')")
    public ResponseEntity<Page<TransactionResponse>> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            Pageable pageable) {

        TransactionFilter filter = new TransactionFilter(
                CurrentUser.require().userId(), categoryId, type, fromDate, toDate, minAmount, maxAmount);
        return ResponseEntity.ok(statementMerchantEnricher.enrich(transactionService.list(filter, pageable)));
    }
}
