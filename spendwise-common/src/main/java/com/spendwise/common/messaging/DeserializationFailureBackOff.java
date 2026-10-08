package com.spendwise.common.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.io.IOException;
import java.time.Duration;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Milestone 22 — tells a deserialization failure that can never succeed apart
 * from one caused by the Schema Registry being unreachable, because the two
 * need opposite handling.
 *
 * <p>{@code KafkaAvroDeserializer} fetches the writer's schema by the id in
 * each record the first time it meets that id (after every restart, for
 * every id). When that fetch fails, {@code ErrorHandlingDeserializer} turns it
 * into the same {@link DeserializationException} as a record that is not Avro
 * at all. Spring Kafka classifies that exception as fatal by default, so a
 * registry restart would dead-letter every live event a consumer read in the
 * meantime — a budget check or a saga compensation silently skipped. Instead:
 * <ul>
 *   <li><b>Registry unreachable</b> — an {@link IOException} anywhere in the
 *   cause chain (connection refused, timeout), or a Schema Registry
 *   {@code RestClientException} with a 5xx error code: retried every
 *   {@link #REGISTRY_RETRY_INTERVAL}, without limit. The partition waits — the
 *   record cannot be processed without its schema, and it is not poison — and
 *   resumes once the registry answers.</li>
 *   <li><b>Any other deserialization failure</b> (not the Schema Registry wire
 *   format, an unknown schema id, a record type with no generated class): no
 *   retry, straight to {@code <topic>.DLT}.</li>
 *   <li><b>Not a deserialization failure</b> — {@code null}, so the error
 *   handler's default policy applies (3 retries, 1 s apart, then the DLT).</li>
 * </ul>
 * The Schema Registry client is not on this module's classpath, so its
 * exception is recognised by class name and by the {@code "error code: 5xxxx"}
 * suffix the client appends to every error message.
 */
final class DeserializationFailureBackOff implements BiFunction<ConsumerRecord<?, ?>, Exception, BackOff> {

    static final Duration REGISTRY_RETRY_INTERVAL = Duration.ofSeconds(5);

    private static final String REST_CLIENT_EXCEPTION =
            "io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException";
    private static final Pattern SERVER_ERROR_CODE = Pattern.compile("error code: 5\\d*");

    @Override
    public BackOff apply(ConsumerRecord<?, ?> record, Exception exception) {
        if (!hasCause(exception, DeserializationException.class)) {
            return null;
        }
        if (registryUnavailable(exception)) {
            return new FixedBackOff(REGISTRY_RETRY_INTERVAL.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS);
        }
        return new FixedBackOff(0, 0);
    }

    private static boolean registryUnavailable(Throwable exception) {
        for (Throwable t = exception; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof IOException) {
                return true;
            }
            if (REST_CLIENT_EXCEPTION.equals(t.getClass().getName()) && t.getMessage() != null
                    && SERVER_ERROR_CODE.matcher(t.getMessage()).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable t = exception; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
