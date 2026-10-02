package com.spendwise.analyticsservice.config;

import com.spendwise.common.exception.AbstractReactiveGlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Activates the shared reactive RFC 7807 ProblemDetail handling from
 * spendwise-common for this service (Milestone 15) — only the
 * {@code @RestControllerAdvice} annotation and component-scan visibility live
 * here; all mapping logic is inherited from
 * {@link AbstractReactiveGlobalExceptionHandler}. Replaces this class's own
 * previous servlet-based {@code AbstractGlobalExceptionHandler} parent, which
 * this service's WebFlux rebuild can no longer extend (see that class's own
 * Javadoc for why) — analytics-service is the platform's first and only
 * consumer of the reactive variant.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractReactiveGlobalExceptionHandler {
}
