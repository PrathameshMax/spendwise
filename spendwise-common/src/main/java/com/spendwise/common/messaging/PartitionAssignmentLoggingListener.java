package com.spendwise.common.messaging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Milestone 18 — logs every partition assignment change on this instance, so
 * a consumer-group rebalance (Milestone 18's chaos lab) can be read straight
 * from {@code docker compose logs}: which partitions this instance gained,
 * which it gave back, and which it lost without a clean hand-off.
 *
 * <p>With the cooperative-sticky assignor this platform configures, a
 * rebalance is incremental: an instance keeps the partitions it already owns
 * and only the ones that must move are revoked and re-assigned, so these log
 * lines show exactly the partitions that moved, not a full revoke-everything
 * round. {@code onPartitionsLost} is the unclean case — the group moved on
 * without this instance (it missed heartbeats for longer than
 * {@code session.timeout.ms}), so its offsets for those partitions may not have
 * been committed and another instance may reprocess some records. Spring
 * Kafka follows a lost callback with a revoked callback for the same
 * partitions, so a WARN "lost" line is always followed by an INFO "revoked"
 * line — one event, not two.
 *
 * <p>Applied to every listener container by Spring Boot, which wires a single
 * {@link ConsumerAwareRebalanceListener} bean into the auto-configured
 * container factory.
 */
public class PartitionAssignmentLoggingListener implements ConsumerAwareRebalanceListener {

    private static final Logger log = LoggerFactory.getLogger(PartitionAssignmentLoggingListener.class);

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log.info("Partitions newly assigned to this instance: {}", describe(partitions));
    }

    @Override
    public void onPartitionsRevokedBeforeCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        if (!partitions.isEmpty()) {
            log.info("Partitions revoked from this instance: {}", describe(partitions));
        }
    }

    @Override
    public void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log.warn("Partitions lost by this instance (left the group without a clean revoke): {}",
                describe(partitions));
    }

    static String describe(Collection<TopicPartition> partitions) {
        if (partitions.isEmpty()) {
            return "[] (none new - see /actuator/kafkaassignments for the full current set)";
        }
        return partitions.stream()
                .map(tp -> tp.topic() + "-" + tp.partition())
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
