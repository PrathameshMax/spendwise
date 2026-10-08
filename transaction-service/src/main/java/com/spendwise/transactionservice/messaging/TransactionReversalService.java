package com.spendwise.transactionservice.messaging;

import com.spendwise.common.messaging.EventIdentity;
import com.spendwise.common.messaging.JdbcProcessedEventGuard;
import com.spendwise.transactionservice.domain.Transaction;
import com.spendwise.transactionservice.domain.TransactionRepository;
import com.spendwise.transactionservice.event.TransactionReversedEvent;
import com.spendwise.transactionservice.service.OutboxEventRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Milestone 21 — the compensating transaction of the budget-breach saga
 * (Functional Roadmap, Workflow 2, Step 4), as one local transaction:
 *
 * <ol>
 *   <li>claim the event in {@code processed_events};</li>
 *   <li>mark the original entry REVERSED (it stays in the ledger);</li>
 *   <li>append the reversing entry (negated amount, status REVERSAL);</li>
 *   <li>record {@code TransactionReversedEvent} in the outbox.</li>
 * </ol>
 *
 * All four commit together or not at all. The outbox — not a direct
 * {@code KafkaTemplate} send — is what makes step 4 safe: the reversal and the
 * fact that it happened are one database commit, and the existing relay
 * (Milestone 17) delivers the event at least once afterwards.
 *
 * <p><b>Three layers against a double reversal.</b> A compensation applied
 * twice would be its own corruption, so: the {@code processed_events} claim
 * discards a redelivered event; the POSTED check makes a <em>different</em>
 * rejection event for an already-reversed entry a no-op (a SAGA's
 * compensations must be idempotent at the business level too, not only per
 * message id); and the V6 migration's unique index on
 * {@code reverses_transaction_id} makes a second reversing entry impossible
 * at the database.
 *
 * <p>The original entry is loaded with a plain {@code findById}: there is one
 * consumer thread per instance, events about one transaction share a
 * partition (key = transaction id), and the unique index is the backstop
 * if two instances ever raced.
 */
@Service
public class TransactionReversalService {

    static final String CONSUMER = "transaction-reversals";

    private static final Logger log = LoggerFactory.getLogger(TransactionReversalService.class);

    private final JdbcProcessedEventGuard processedEventGuard;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    public TransactionReversalService(JdbcProcessedEventGuard processedEventGuard,
                                      TransactionRepository transactionRepository,
                                      OutboxEventRecorder outboxEventRecorder) {
        this.processedEventGuard = processedEventGuard;
        this.transactionRepository = transactionRepository;
        this.outboxEventRecorder = outboxEventRecorder;
    }

    @Transactional
    public ReversalOutcome reverse(EventIdentity eventIdentity, TransactionRejectedEvent event) {
        if (!processedEventGuard.claim(CONSUMER, eventIdentity)) {
            log.info("Duplicate delivery of event {} for transaction {} discarded",
                    eventIdentity, event.transactionId());
            return ReversalOutcome.DUPLICATE;
        }
        Optional<Transaction> found = transactionRepository.findById(event.transactionId())
                .filter(transaction -> transaction.getUserId().equals(event.userId()));
        if (found.isEmpty()) {
            log.warn("Rejection {} names transaction {} for user {}, which does not exist; nothing to reverse",
                    eventIdentity, event.transactionId(), event.userId());
            return ReversalOutcome.UNKNOWN_TRANSACTION;
        }
        Transaction original = found.get();
        if (!original.isPosted()) {
            log.info("Transaction {} is already {}; rejection {} changes nothing",
                    original.getId(), original.getStatus(), eventIdentity);
            return ReversalOutcome.ALREADY_REVERSED;
        }

        original.markReversed(event.reason());
        Transaction reversal = transactionRepository.save(Transaction.reversalOf(original, event.reason()));

        outboxEventRecorder.record(original.getId(), new TransactionReversedEvent(
                original.getId(),
                reversal.getId(),
                original.getUserId(),
                original.getCategory().getId(),
                original.getCategory().getName(),
                original.getAmount(),
                original.getType(),
                original.getTransactionDate(),
                original.getCurrency(),
                original.getBaseCurrencyAmount(),
                event.reason(),
                Instant.now()));

        log.info("Transaction {} reversed by entry {} ({} {}): {}", original.getId(), reversal.getId(),
                reversal.getAmount(), reversal.getCurrency(), event.reason());
        return ReversalOutcome.REVERSED;
    }
}
