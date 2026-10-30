package com.spendwise.transactionservice.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * CR-2: Redis client behaviour for the merchant-details cache.
 *
 * <p>By default Lettuce queues commands while it is disconnected and each one waits
 * for the full command timeout. With {@code REJECT_COMMANDS} a down Redis fails
 * immediately, so the cache is skipped at near-zero cost instead of adding the
 * timeout to every statement request. Same setting as the API gateway's rate limiter.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientOptionsBuilderCustomizer rejectCommandsWhileDisconnected(){
        return clientOptionsBuilder -> clientOptionsBuilder
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true);
    }
}
