package com.spendwise.common.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Milestone 20 — {@code spendwise.kafka.error-handling.*}, the bounded retry
 * policy applied to every listener before a record is dead-lettered.
 *
 * @param maxRetries    retries after the first failed attempt; with the default
 *                      of 3 a poison record is attempted 4 times in total, then
 *                      published to its {@code .DLT} topic
 * @param retryInterval fixed wait between attempts. Fixed rather than
 *                      exponential: the failures worth retrying here (a
 *                      database blip, a lock timeout) clear in about a second,
 *                      and the partition is held while retries run, so the total
 *                      ({@code maxRetries x retryInterval}, 3 s by default) has
 *                      to stay short
 */
@ConfigurationProperties("spendwise.kafka.error-handling")
public record KafkaErrorHandlingProperties(
        @DefaultValue("3") long maxRetries,
        @DefaultValue("1s") Duration retryInterval) {
}
