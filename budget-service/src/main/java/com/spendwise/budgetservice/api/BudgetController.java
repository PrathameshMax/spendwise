package com.spendwise.budgetservice.api;

import com.spendwise.budgetservice.service.BudgetService;
import com.spendwise.common.security.CurrentUser;
import jakarta.validation.Valid;
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

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Customers act only on their own budgets (owner = token {@code sub}); support
 * staff with {@code ROLE_ADMIN} may read any user's summary, and that access is
 * audited.
 */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('SCOPE_budgets:write')")
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(budgetService.create(CurrentUser.require().userId(), request));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('SCOPE_budgets:read')")
    public ResponseEntity<List<BudgetResponse>> mySummary(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodMonth) {
        return ResponseEntity.ok(budgetService.summary(CurrentUser.require().userId(), periodMonth));
    }

    @GetMapping("/users/{userId}/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BudgetResponse>> summaryForUser(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodMonth) {
        return ResponseEntity.ok(budgetService.summaryForAdmin(CurrentUser.require(), userId, periodMonth));
    }
}
