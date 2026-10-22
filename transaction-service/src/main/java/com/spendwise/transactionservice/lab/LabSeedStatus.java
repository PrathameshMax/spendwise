package com.spendwise.transactionservice.lab;

import java.time.Instant;

/** Response of every {@code /lab/seed} call: the current job plus what the database holds. */
public record LabSeedStatus(Job job, LabDataSummary data) {

    public record Job(
            String operation,
            String state,
            String parameters,
            Instant startedAt,
            Instant finishedAt,
            long elapsedSeconds,
            long processedRows,
            long estimatedRows,
            Integer percentComplete,
            String message) {
    }
}
