package com.spendwise.transactionservice.lab;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Progress of the one background lab job (seed or cleanup) the service runs
 * at a time. Fields are written by the worker thread and read by
 * {@code GET /lab/seed}, hence volatile/atomic.
 */
final class LabJob {

    enum Operation { NONE, SEED, CLEANUP }

    enum State { IDLE, RUNNING, COMPLETED, FAILED }

    private final Operation operation;
    private final Instant startedAt;
    private final long estimatedRows;
    private final String parameters;
    private final AtomicLong processedRows = new AtomicLong();
    private volatile State state;
    private volatile Instant finishedAt;
    private volatile String message;

    private LabJob(Operation operation, State state, long estimatedRows, String parameters, String message) {
        this.operation = operation;
        this.state = state;
        this.startedAt = operation == Operation.NONE ? null : Instant.now();
        this.estimatedRows = estimatedRows;
        this.parameters = parameters;
        this.message = message;
    }

    static LabJob idle() {
        return new LabJob(Operation.NONE, State.IDLE, 0, null, "No lab job has run since startup.");
    }

    static LabJob seed(long estimatedRows, String parameters) {
        return new LabJob(Operation.SEED, State.RUNNING, estimatedRows, parameters, "Seeding started.");
    }

    static LabJob cleanup(long estimatedRows) {
        return new LabJob(Operation.CLEANUP, State.RUNNING, estimatedRows, null, "Cleanup started.");
    }

    boolean isRunning() {
        return state == State.RUNNING;
    }

    void addProcessed(long rows) {
        processedRows.addAndGet(rows);
    }

    long processedRows() {
        return processedRows.get();
    }

    void complete(String finalMessage) {
        this.message = finalMessage;
        this.finishedAt = Instant.now();
        this.state = State.COMPLETED;
    }

    void fail(String failureMessage) {
        this.message = failureMessage;
        this.finishedAt = Instant.now();
        this.state = State.FAILED;
    }

    Duration elapsed() {
        if (startedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(startedAt, finishedAt == null ? Instant.now() : finishedAt);
    }

    LabSeedStatus.Job view() {
        long processed = processedRows.get();
        Integer percent = null;
        if (estimatedRows > 0) {
            percent = (int) Math.min(100, processed * 100 / estimatedRows);
            if (state == State.COMPLETED) {
                percent = 100;
            }
        }
        return new LabSeedStatus.Job(
                operation.name(),
                state.name(),
                parameters,
                startedAt,
                finishedAt,
                elapsed().toSeconds(),
                processed,
                estimatedRows,
                percent,
                message);
    }
}
