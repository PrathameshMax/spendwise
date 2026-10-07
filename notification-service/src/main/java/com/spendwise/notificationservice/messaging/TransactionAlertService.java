package com.spendwise.notificationservice.messaging;

import com.spendwise.common.messaging.EventIdentity;
import com.spendwise.common.tracing.CorrelationIdConstants;
import com.spendwise.notificationservice.domain.NotificationLog;
import com.spendwise.notificationservice.domain.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Milestone 19 — the idempotent unit of work behind
 * {@link TransactionAlertListener}: claim the event, record the alert, and
 * dispatch it only once both are committed.
 *
 * <p><b>One transaction.</b> {@link ProcessedEventGuard#claim} and the
 * {@code notification_log} insert commit together. If anything fails after
 * the claim, both roll back, the listener rethrows, and the retry is treated
 * as a first delivery again — no event is ever marked processed without its
 * alert, and no alert is recorded twice.
 *
 * <p><b>Dispatch after commit.</b> The simulated push is registered as an
 * {@code afterCommit} callback rather than performed inline. A real push
 * (email/SMS/WebSocket) cannot be rolled back, so sending it inside the
 * transaction would mean a failed commit — or a crash between send and
 * commit — leaves a sent alert with no record of it, and the retry sends it
 * again. Sending after commit narrows that to the one remaining gap: a crash
 * after commit but before the send loses that send (the event is already
 * marked processed). For an alert, at-most-once delivery from this point is
 * the right trade; a channel that needs stronger guarantees would get its own
 * outbox, as transaction-service has.
 */
@Service
public class TransactionAlertService {

    static final String CONSUMER = "transaction-alerts";

    private static final Logger log = LoggerFactory.getLogger(TransactionAlertService.class);

    private final ProcessedEventGuard processedEventGuard;
    private final NotificationLogRepository notificationLogRepository;

    public TransactionAlertService(ProcessedEventGuard processedEventGuard,
                                   NotificationLogRepository notificationLogRepository) {
        this.processedEventGuard = processedEventGuard;
        this.notificationLogRepository = notificationLogRepository;
    }

    /**
     * @return {@code true} if the alert was recorded (first delivery),
     * {@code false} if this event had already been processed and was skipped.
     */
    @Transactional
    public boolean recordAlert(EventIdentity eventIdentity, TransactionCreatedEvent event) {
        if (!processedEventGuard.claim(CONSUMER, eventIdentity)) {
            log.info("Duplicate delivery of event {} for transaction {} discarded",
                    eventIdentity, event.transactionId());
            return false;
        }

        String message = "%s of %s %s recorded for %s".formatted(
                event.type(), event.amount(), event.currency(), event.transactionDate());
        NotificationLog alert = notificationLogRepository.save(new NotificationLog(
                eventIdentity.id(), event.userId(), event.transactionId(),
                NotificationLog.CHANNEL_SIMULATED_PUSH, message, MDC.get(CorrelationIdConstants.MDC_KEY)));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("[SIMULATED PUSH] to user {}: {} (transaction {}, alert {})",
                        event.userId(), message, event.transactionId(), alert.getId());
            }
        });
        return true;
    }
}
