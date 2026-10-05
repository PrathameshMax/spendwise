package com.spendwise.common.messaging;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * Milestone 18 — {@code GET /actuator/kafkaassignments}: which topic
 * partitions this instance's listener containers currently own.
 *
 * <p>This is what makes the consumer-group chaos lab checkable from Postman
 * rather than only from logs: scale one service to four instances, call this
 * endpoint on each instance's host port, and the answer is visible directly —
 * three instances each own one partition and the fourth owns none. Kill an
 * active instance, call it again on the survivors, and the orphaned partition
 * shows up on the instance that was idle.
 *
 * <p>Read-only, and exposes only topic names, partition numbers, group ids and
 * the container's hostname — no record data or offsets. It is exposed per
 * service through {@code management.endpoints.web.exposure.include}, on the
 * services that have listeners.
 */
@Endpoint(id = "kafkaassignments")
public class KafkaAssignmentsEndpoint {

    static final String REBALANCING = "rebalancing - retry";
    private static final int SNAPSHOT_ATTEMPTS = 5;

    private final KafkaListenerEndpointRegistry registry;

    public KafkaAssignmentsEndpoint(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @ReadOperation
    public Assignments assignments() {
        List<ListenerAssignment> listeners = registry.getListenerContainers().stream()
                .map(KafkaAssignmentsEndpoint::toAssignment)
                .sorted(Comparator.comparing(ListenerAssignment::listenerId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new Assignments(instanceName(), listeners);
    }

    private static ListenerAssignment toAssignment(MessageListenerContainer container) {
        return new ListenerAssignment(container.getListenerId(), container.getGroupId(),
                container.isRunning(), snapshot(container));
    }

    /**
     * The container returns a read-only <em>view</em> of the set its consumer
     * thread mutates during a rebalance, not a copy, so iterating it here on
     * the HTTP thread can race a rebalance and throw
     * {@link ConcurrentModificationException}. A rebalance's mutation is a
     * single quick addAll/removeAll, so retrying the copy a few times is
     * enough; if it still races, report the call as unsettled rather than 500.
     */
    private static List<String> snapshot(MessageListenerContainer container) {
        for (int attempt = 1; attempt <= SNAPSHOT_ATTEMPTS; attempt++) {
            try {
                Collection<TopicPartition> assigned = assignedPartitions(container);
                if (assigned == null) {
                    return List.of();
                }
                return List.copyOf(assigned).stream()
                        .map(tp -> tp.topic() + "-" + tp.partition())
                        .sorted()
                        .toList();
            } catch (ConcurrentModificationException ex) {
                Thread.onSpinWait();
            }
        }
        return List.of(REBALANCING);
    }

    /**
     * {@code ConcurrentMessageListenerContainer} (what Boot's factory builds)
     * answers this from its child containers; the interface default throws
     * for container types that do not track assignments, which must not turn
     * a read-only diagnostics call into a 500.
     */
    private static Collection<TopicPartition> assignedPartitions(MessageListenerContainer container) {
        try {
            return container.getAssignedPartitions();
        } catch (UnsupportedOperationException ex) {
            return List.of();
        }
    }

    /**
     * Docker sets {@code HOSTNAME} to the container id, which is what tells
     * four replicas of the same service apart; {@code COMPUTERNAME} covers a
     * service run directly on Windows.
     */
    private static String instanceName() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            hostname = System.getenv("COMPUTERNAME");
        }
        return hostname != null ? hostname : "unknown";
    }

    public record Assignments(String instance, List<ListenerAssignment> listeners) {
    }

    public record ListenerAssignment(String listenerId, String groupId, boolean running,
                                     List<String> assignedPartitions) {
    }
}
