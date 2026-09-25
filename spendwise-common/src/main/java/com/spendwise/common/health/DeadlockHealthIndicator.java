package com.spendwise.common.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Backs the "deadlock" component of the liveness health group (Milestone 5).
 * Uses {@link ThreadMXBean#findDeadlockedThreads()} to detect genuine monitor or
 * ownable-synchronizer deadlocks, not a synthetic/cosmetic check — this is the same
 * mechanism a manual jstack-based diagnosis would use, exposed continuously instead
 * of on demand.
 */
public class DeadlockHealthIndicator implements HealthIndicator {

    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    @Override
    public Health health() {
        long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
        int count = deadlockedThreadIds == null ? 0 : deadlockedThreadIds.length;

        Health.Builder builder = count == 0 ? Health.up() : Health.down();
        builder.withDetail("deadlockedThreads", count);
        if (count > 0) {
            builder.withDetail("threadIds", deadlockedThreadIds);
        }
        return builder.build();
    }
}
