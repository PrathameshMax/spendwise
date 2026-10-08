package com.spendwise.analyticsservice.messaging;

import com.spendwise.common.messaging.EventIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Milestone 19 — the idempotent unit of work behind
 * {@link TransactionIngestionListener}, on the reactive stack: claim the
 * event in {@code processed_events} and ingest it, inside one R2DBC
 * transaction opened by {@link TransactionalOperator} (Spring's reactive
 * equivalent of {@code @Transactional}, auto-configured over Boot's
 * {@code R2dbcTransactionManager}).
 *
 * <p>Ingestion itself is still a log line, written by the listener — the
 * monthly-trend and category-breakdown projections are Milestone 23, and will
 * be written inside this same transactional pipeline, after the claim. That
 * is the point of having the guard here now: once a projection update
 * commits only together with its claim, replaying a duplicate can never
 * double-count a transaction in the read model.
 *
 * <p>Nothing is logged in this pipeline: its operators run on the R2DBC
 * driver's I/O thread, where the record's correlation id (set in the MDC on
 * the Kafka consumer thread) is not visible, and before the commit. The
 * listener logs the outcome after {@code block()} returns instead — on the
 * consumer thread, and only once the transaction has committed. The
 * {@code event} parameters are unused until Milestone 23 writes projections from them.
 */
@Service
public class TransactionIngestionService {

    static final String CONSUMER = "transaction-ingestion";

    private final ProcessedEventGuard processedEventGuard;
    private final TransactionalOperator transactionalOperator;

    public TransactionIngestionService(ProcessedEventGuard processedEventGuard,
                                       TransactionalOperator transactionalOperator) {
        this.processedEventGuard = processedEventGuard;
        this.transactionalOperator = transactionalOperator;
    }

    /**
     * @return a {@code Mono} of {@code true} if the event was ingested (first
     * delivery), {@code false} if it had already been processed.
     */
    public Mono<Boolean> ingest(EventIdentity eventIdentity, TransactionCreatedEvent event) {
        return processedEventGuard.claim(CONSUMER, eventIdentity)
                .as(transactionalOperator::transactional);
    }

    /**
     * Milestone 21 — the read side of the saga's compensation (Functional
     * Roadmap, Workflow 2, Step 6): a reversed entry must leave the read model
     * exactly as if it had never been ingested. Same claim, same transaction
     * as {@link #ingest}, so a redelivered reversal can never retract twice;
     * Milestone 23 subtracts the entry from the projections inside this
     * pipeline, after the claim.
     *
     * @return a {@code Mono} of {@code true} on first delivery, {@code false}
     * for a duplicate
     */
    public Mono<Boolean> retract(EventIdentity eventIdentity, TransactionReversedEvent event) {
        return processedEventGuard.claim(CONSUMER, eventIdentity)
                .as(transactionalOperator::transactional);
    }
}
