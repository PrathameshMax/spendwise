package com.spendwise.analyticsservice.api;

import com.spendwise.analyticsservice.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * GET /api/v1/analytics/dashboard/{userId}?periodMonth=2026-09
     */
    @GetMapping("/dashboard/{userId}")
    public Mono<ResponseEntity<DashboardResponse>> dashboard(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodMonth) {
        return analyticsService.getDashboard(userId, periodMonth).map(ResponseEntity::ok);
    }
}
