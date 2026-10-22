package com.spendwise.transactionservice.lab;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs lab seed and cleanup jobs on a single background worker so the HTTP
 * call returns at once (202) and progress is polled with {@code GET /lab/seed}.
 * Only one job runs at a time; a seed is refused while lab data already
 * exists, so every exercise starts from a known, reproducible state.
 */
@Service
@Profile("lab")
public class LabSeedService {

    private static final Logger log = LoggerFactory.getLogger(LabSeedService.class);
    private static final int INSERT_BATCH_SIZE = 5_000;
    private static final int DELETE_USER_BLOCK = 200;

    private final LabSeedRepository repository;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lab-seed-worker");
        thread.setDaemon(true);
        return thread;
    });
    private volatile LabJob current = LabJob.idle();

    public LabSeedService(LabSeedRepository repository) {
        this.repository = repository;
    }

    public synchronized LabSeedStatus startSeed(LabSeedRequest request) {
        int users = request.usersOrDefault();
        int months = request.monthsOrDefault();
        long seed = request.seedOrDefault();
        if ((long) users * months > LabSeedRequest.MAX_USER_MONTHS) {
            throw LabRequestException.tooLarge(users, months);
        }
        if (current.isRunning()) {
            throw LabRequestException.jobRunning(current.view().operation());
        }
        long existing = repository.countLabTransactions();
        if (existing > 0) {
            throw LabRequestException.dataPresent(existing);
        }

        long estimate = (long) users * months * LabDataGenerator.ESTIMATED_ROWS_PER_USER_MONTH;
        String parameters = "users=" + users + ", months=" + months + ", randomSeed=" + seed;
        LabJob job = LabJob.seed(estimate, parameters);
        current = job;
        worker.execute(() -> runSeed(job, users, months, seed));
        return status();
    }

    public synchronized LabSeedStatus startCleanup() {
        if (current.isRunning()) {
            throw LabRequestException.jobRunning(current.view().operation());
        }
        LabJob job = LabJob.cleanup(repository.countLabTransactions());
        current = job;
        worker.execute(() -> runCleanup(job));
        return status();
    }

    public LabSeedStatus status() {
        return new LabSeedStatus(current.view(), repository.summary());
    }

    private void runSeed(LabJob job, int users, int months, long seed) {
        LocalDate lastDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        LabDataGenerator generator = new LabDataGenerator(seed, months, lastDate);
        log.info("Lab seed started: users={} months={} randomSeed={} dates={}..{}",
                users, months, seed, generator.firstDate(), lastDate);
        try {
            Map<LabCategory, UUID> categoryIds = repository.ensureCategories();
            List<LabTransactionRow> batch = new ArrayList<>(INSERT_BATCH_SIZE + 2_000);
            int nextProgressLog = 10;
            for (int userIndex = 1; userIndex <= users; userIndex++) {
                if (Thread.currentThread().isInterrupted()) {
                    job.fail("Interrupted after " + job.processedRows() + " rows (service shutting down).");
                    log.warn("Lab seed interrupted after {} rows", job.processedRows());
                    return;
                }
                generator.generateUser(userIndex, batch::add);
                if (batch.size() >= INSERT_BATCH_SIZE) {
                    flush(batch, categoryIds, job);
                }
                int percentUsers = (int) ((long) userIndex * 100 / users);
                if (percentUsers >= nextProgressLog) {
                    log.info("Lab seed progress: {}% of users, {} rows inserted", percentUsers, job.processedRows());
                    nextProgressLog += 10;
                }
            }
            flush(batch, categoryIds, job);
            repository.analyzeTransactions();

            long rows = job.processedRows();
            long seconds = Math.max(1, job.elapsed().toSeconds());
            String done = "Seeded " + rows + " transactions for " + users + " users from "
                    + generator.firstDate() + " to " + lastDate + " in " + seconds + "s ("
                    + (rows / seconds) + " rows/s).";
            job.complete(done);
            log.info("Lab seed completed: rows={} users={} seconds={}", rows, users, seconds);
        } catch (RuntimeException ex) {
            job.fail("Seed failed after " + job.processedRows() + " rows: " + ex.getMessage());
            log.error("Lab seed failed after {} rows", job.processedRows(), ex);
        }
    }

    private void flush(List<LabTransactionRow> batch, Map<LabCategory, UUID> categoryIds, LabJob job) {
        if (batch.isEmpty()) {
            return;
        }
        repository.insertBatch(batch, categoryIds);
        job.addProcessed(batch.size());
        batch.clear();
    }

    private void runCleanup(LabJob job) {
        log.info("Lab cleanup started: labTransactions={}", job.view().estimatedRows());
        try {
            int maxUser = repository.maxLabUserIndex();
            for (int from = 1; from <= maxUser; from += DELETE_USER_BLOCK) {
                if (Thread.currentThread().isInterrupted()) {
                    job.fail("Interrupted after deleting " + job.processedRows() + " rows (service shutting down).");
                    log.warn("Lab cleanup interrupted after {} rows", job.processedRows());
                    return;
                }
                int to = (int) Math.min((long) from + DELETE_USER_BLOCK - 1, maxUser);
                job.addProcessed(repository.deleteLabUsers(from, to));
            }
            int categories = repository.deleteUnusedLabCategories();
            repository.analyzeTransactions();

            String done = "Deleted " + job.processedRows() + " lab transactions and " + categories
                    + " lab categories in " + job.elapsed().toSeconds() + "s. Data created through the real APIs was not touched.";
            job.complete(done);
            log.info("Lab cleanup completed: transactions={} categories={}", job.processedRows(), categories);
        } catch (RuntimeException ex) {
            job.fail("Cleanup failed after " + job.processedRows() + " rows: " + ex.getMessage());
            log.error("Lab cleanup failed after {} rows", job.processedRows(), ex);
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
