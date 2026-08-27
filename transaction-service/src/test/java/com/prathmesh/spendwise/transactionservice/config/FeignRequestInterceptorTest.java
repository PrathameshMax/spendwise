package com.prathmesh.spendwise.transactionservice.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class FeignRequestInterceptorTest {

    private final FeignRequestInterceptor interceptor =
            new FeignRequestInterceptor();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    void shouldPropagateRequestIdToFeignRequest() {

        MDC.put("requestId", "test-request-123");

        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertEquals(
                "test-request-123",
                template
                        .headers()
                        .get("X-Request-ID")
                        .iterator()
                        .next()
        );
    }

    @Test
    void shouldNotAddRequestIdWhenMdcIsEmpty() {

        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertFalse(
                template.headers().containsKey("X-Request-ID")
        );
    }
}
