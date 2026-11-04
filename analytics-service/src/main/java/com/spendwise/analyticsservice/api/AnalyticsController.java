package com.spendwise.analyticsservice.api;

import com.spendwise.analyticsservice.service.AnalyticsService;
import com.spendwise.common.security.ReactiveCurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.YearMonth;

/**
 * The dashboard is always the caller's: the user id is read from the validated
 * token in the Reactor context, so {@code /dashboard/{someone-else}} no longer
 * exists as an attack surface.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('SCOPE_transactions:read')")
    public Mono<ResponseEntity<DashboardResponse>> dashboard(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodMonth) {
        return ReactiveCurrentUser.require()
                .flatMap(user -> analyticsService.getDashboard(user.userId(), periodMonth))
                .map(ResponseEntity::ok);
    }
}
