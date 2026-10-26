package com.spendwise.transactionservice.api;

import com.spendwise.transactionservice.service.InsightsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/transactions/insights")
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    /**
     * Home screen "This month" card.
     * Example: GET /api/v1/transactions/insights/monthly?userId=...&month=2026-10
     */
    @GetMapping("/monthly")
    public ResponseEntity<MonthlyInsightsResponse> monthly(
            @RequestParam String userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(insightsService.monthly(userId, month));
    }
}
