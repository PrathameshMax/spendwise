package com.spendwise.notificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Milestone 19 — one alert this service raised. Written in the same local
 * transaction as the {@code processed_events} claim, so "the alert was
 * recorded" and "the event is marked processed" are a single fact: both
 * commit, or neither does. That is what lets a redelivered event be skipped
 * safely — if the claim exists, so does this row.
 */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

    public static final String CHANNEL_SIMULATED_PUSH = "SIMULATED_PUSH";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, length = 200)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationLog() {
        // required by JPA
    }

    public NotificationLog(String eventId, UUID userId, UUID transactionId, String channel, String message,
                           String correlationId) {
        this.eventId = eventId;
        this.userId = userId;
        this.transactionId = transactionId;
        this.channel = channel;
        this.message = message;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getChannel() {
        return channel;
    }

    public String getMessage() {
        return message;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
