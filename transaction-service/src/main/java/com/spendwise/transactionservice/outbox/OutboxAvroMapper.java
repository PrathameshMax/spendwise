package com.spendwise.transactionservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.transactionservice.domain.OutboxEvent;
import com.spendwise.transactionservice.event.TransactionCreatedEvent;
import com.spendwise.transactionservice.event.TransactionReversedEvent;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Milestone 22 — converts an outbox row into the Avro record that goes on the
 * wire, at publish time.
 *
 * <p><b>Why the outbox keeps JSON.</b> {@code outbox_events.payload} stays the
 * Jackson JSON of transaction-service's own event records, written in the
 * business transaction exactly as since Milestone 16. The Avro contract is
 * applied at the edge, by the relay:
 * <ul>
 *   <li>rows already in the table when this release starts (written as JSON
 *   by the previous release) are published as Avro like any other — no
 *   migration of pending rows, and none of them is lost in the upgrade;</li>
 *   <li>the business write never talks to the Schema Registry. A registry
 *   outage, or a schema the registry refuses, delays publishing (the row stays
 *   unprocessed and is retried every poll) instead of failing
 *   {@code POST /api/v1/transactions}.</li>
 * </ul>
 *
 * <p><b>Scale 2.</b> Avro's {@code decimal(19,2)} carries an unscaled integer
 * at scale 2. Avro widens a smaller scale itself ({@code 5000} becomes
 * {@code 5000.00}), but refuses a value that would need rounding — and a
 * converted {@code baseCurrencyAmount} is {@code amount × rate} with many
 * digits, which the payload carries as computed in memory, not as stored.
 * So every amount is set to scale 2 here, rounding half away from zero
 * ({@code HALF_UP}) — the rounding PostgreSQL applied when the same value was
 * written to its {@code NUMERIC(19,2)} column, so the event and the ledger row
 * agree to the cent.
 */
@Component
public class OutboxAvroMapper {

    private static final int MONEY_SCALE = 2;

    private final ObjectMapper objectMapper;

    public OutboxAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @throws IllegalStateException for an event type with no Avro contract or a
     *                               payload that does not parse — the relay
     *                               treats it as a failed publish and retries
     */
    public SpecificRecord toAvro(OutboxEvent event) {
        return switch (event.getEventType()) {
            case "TransactionCreatedEvent" -> created(read(event, TransactionCreatedEvent.class));
            case "TransactionReversedEvent" -> reversed(read(event, TransactionReversedEvent.class));
            default -> throw new IllegalStateException(
                    "No Avro contract for outbox event type " + event.getEventType() + " (row " + event.getId() + ")");
        };
    }

    private static com.spendwise.events.avro.TransactionCreatedEvent created(TransactionCreatedEvent e) {
        return com.spendwise.events.avro.TransactionCreatedEvent.newBuilder()
                .setTransactionId(e.transactionId().toString())
                .setUserId(e.userId().toString())
                .setCategoryId(e.categoryId().toString())
                .setCategoryName(e.categoryName())
                .setAmount(money(e.amount()))
                .setType(e.type().name())
                .setTransactionDate(e.transactionDate())
                .setCurrency(e.currency())
                .setBaseCurrencyAmount(money(e.baseCurrencyAmount()))
                .setCreatedAt(e.createdAt())
                .build();
    }

    private static com.spendwise.events.avro.TransactionReversedEvent reversed(TransactionReversedEvent e) {
        return com.spendwise.events.avro.TransactionReversedEvent.newBuilder()
                .setTransactionId(e.transactionId().toString())
                .setReversalTransactionId(e.reversalTransactionId().toString())
                .setUserId(e.userId().toString())
                .setCategoryId(e.categoryId().toString())
                .setCategoryName(e.categoryName())
                .setAmount(money(e.amount()))
                .setType(e.type().name())
                .setTransactionDate(e.transactionDate())
                .setCurrency(e.currency())
                .setBaseCurrencyAmount(money(e.baseCurrencyAmount()))
                .setReason(e.reason())
                .setReversedAt(e.reversedAt())
                .build();
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private <T> T read(OutboxEvent event, Class<T> type) {
        try {
            return objectMapper.readValue(event.getPayload(), type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Outbox row %s: payload is not a valid %s"
                    .formatted(event.getId(), type.getSimpleName()), ex);
        }
    }
}
