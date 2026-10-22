package com.spendwise.transactionservice.lab;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Production Debugging Lab — test-data endpoints. Exist only when the
 * {@code lab} Spring profile is active (docker-compose.lab.yml), are served on
 * transaction-service's own port (8083) and are not routed by the API gateway,
 * the same way a bank keeps test-data tooling off the public edge.
 *
 * <ul>
 *   <li>{@code POST /lab/seed} — start generating data (202, poll the status)</li>
 *   <li>{@code GET /lab/seed} — current job progress and row counts</li>
 *   <li>{@code DELETE /lab/seed} — remove lab data only (202, poll the status)</li>
 * </ul>
 */
@RestController
@Profile("lab")
@RequestMapping("/lab/seed")
public class LabSeedController {

    private final LabSeedService labSeedService;

    public LabSeedController(LabSeedService labSeedService) {
        this.labSeedService = labSeedService;
    }

    @PostMapping
    public ResponseEntity<LabSeedStatus> seed(@RequestBody(required = false) @Valid LabSeedRequest request) {
        LabSeedRequest effective = request == null ? new LabSeedRequest(null, null, null) : request;
        return ResponseEntity.accepted().body(labSeedService.startSeed(effective));
    }

    @GetMapping
    public ResponseEntity<LabSeedStatus> status() {
        return ResponseEntity.ok(labSeedService.status());
    }

    @DeleteMapping
    public ResponseEntity<LabSeedStatus> cleanup() {
        return ResponseEntity.accepted().body(labSeedService.startCleanup());
    }
}
