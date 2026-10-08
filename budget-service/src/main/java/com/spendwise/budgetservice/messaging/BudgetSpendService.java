package com.spendwise.budgetservice.messaging;

import com.spendwise.budgetservice.domain.Budget;
import com.spendwise.budgetservice.domain.BudgetRepository;
import com.spendwise.common.messaging.EventIdentity;
import com.spendwise.common.messaging.JdbcProcessedEventGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Milestone 21 — the budget step of the Choreographed SAGA (Functional
 * Roadmap, Workflow 2, Steps 2-3), as one local transaction per event.
 *
 * <p>For an expense with a matching budget (same user, category name and
 * month as the transaction date):
 * <ul>
 *   <li>within the cap, or any SOFT budget — add it to {@code currentSpend};</li>
 *   <li>a HARD budget the expense would take past its cap — leave
 *   {@code currentSpend} unchanged and publish a
 *   {@link TransactionRejectedEvent}. transaction-service has already
 *   committed the transaction; this event is what makes it reverse it.</li>
 * </ul>
 *
 * <p><b>One transaction, claim first.</b> The {@code processed_events} claim,
 * the spend update and the rejection publish all happen inside this method's
 * transaction. A redelivered event is discarded by the claim, so spend is
 * never added twice. If the publish fails, the exception rolls back the claim
 * with everything else, and the redelivery re-runs the decision from
 * scratch. If the publish was acknowledged but the commit then failed, the
 * redelivery publishes the rejection again — under the same deterministic
 * event id ({@link BudgetEventPublisher#rejectionEventId}), which
 * transaction-service deduplicates.
 *
 * <p><b>Concurrency.</b> {@code currentSpend} is read, compared and written
 * back by whichever thread holds the event. Events for one user can sit on
 * different partitions (the key is the transaction id), so with several
 * budget-service instances two expenses against the same budget can be
 * decided concurrently from the same starting spend — a lost update, and a
 * HARD cap that two expenses can jointly slip past. With the single instance
 * docker-compose runs, one consumer thread processes every partition in turn
 * and the read-modify-write is serial. Serialising it across instances is
 * Milestone 24's distributed lock.
 *
 * <p><b>A retried decision is re-made, not replayed.</b> In the rare case
 * where the rejection was acknowledged and the commit then failed, the
 * redelivered event is decided again from the budget's spend at that moment.
 * If other expenses were applied in between, the outcome is the same (spend
 * only grows); if the budget's spend or cap had been lowered meanwhile, the
 * retry could apply an expense whose first rejection transaction-service has
 * already acted on. Persisting the decision before publishing it (a
 * budget-side outbox) would close this; at one instance, with spend only ever
 * increased by this consumer, the window is a commit failure in the
 * milliseconds after an acknowledged send.
 *
 * <p>Budgets are matched on the exact category name, case-sensitively, as
 * stored by transaction-service.
 */
@Service
public class BudgetSpendService {

    static final String CONSUMER = "budget-spend";

    private static final Logger log = LoggerFactory.getLogger(BudgetSpendService.class);

    private final JdbcProcessedEventGuard processedEventGuard;
    private final BudgetRepository budgetRepository;
    private final BudgetEventPublisher budgetEventPublisher;

    public BudgetSpendService(JdbcProcessedEventGuard processedEventGuard, BudgetRepository budgetRepository,
                              BudgetEventPublisher budgetEventPublisher) {
        this.processedEventGuard = processedEventGuard;
        this.budgetRepository = budgetRepository;
        this.budgetEventPublisher = budgetEventPublisher;
    }

    @Transactional
    public BudgetDecision apply(EventIdentity eventIdentity, TransactionCreatedEvent event) {
        if (!processedEventGuard.claim(CONSUMER, eventIdentity)) {
            log.info("Duplicate delivery of event {} for transaction {} discarded",
                    eventIdentity, event.transactionId());
            return BudgetDecision.DUPLICATE;
        }
        if (!TransactionCreatedEvent.EXPENSE.equals(event.type())) {
            return BudgetDecision.NOT_EXPENSE;
        }
        if (event.categoryName() == null) {
            log.info("Transaction {} has no category name (event predates Milestone 21); not attributed to a budget",
                    event.transactionId());
            return BudgetDecision.UNATTRIBUTABLE;
        }
        YearMonth periodMonth = YearMonth.from(event.transactionDate());
        Optional<Budget> match = budgetRepository.findByUserIdAndCategoryAndPeriodMonth(
                event.userId(), event.categoryName(), periodMonth);
        if (match.isEmpty()) {
            log.debug("No budget for user {}, category {}, month {}; transaction {} not enforced",
                    event.userId(), event.categoryName(), periodMonth, event.transactionId());
            return BudgetDecision.NO_BUDGET;
        }
        Budget budget = match.get();
        BigDecimal expense = event.budgetAmount();

        if (budget.wouldBreachHardCap(expense)) {
            BigDecimal wouldBe = budget.getCurrentSpend().add(expense);
            String reason = "HARD budget '%s' for %s: cap %s, spent %s, this expense of %s would make it %s"
                    .formatted(budget.getCategory(), periodMonth, budget.getCappedAmount(),
                            budget.getCurrentSpend(), expense, wouldBe);
            budgetEventPublisher.publishRejection(new TransactionRejectedEvent(
                    event.transactionId(), event.userId(), budget.getId(), budget.getCategory(), periodMonth,
                    budget.getCappedAmount(), budget.getCurrentSpend(), expense, reason, Instant.now()));
            log.info("Transaction {} rejected: {}", event.transactionId(), reason);
            return BudgetDecision.REJECTED;
        }

        budget.applySpend(expense);
        log.info("Transaction {} applied to budget {} ({} {}): spend now {} of {}", event.transactionId(),
                budget.getId(), budget.getCategory(), periodMonth, budget.getCurrentSpend(),
                budget.getCappedAmount());
        return BudgetDecision.APPLIED;
    }
}
