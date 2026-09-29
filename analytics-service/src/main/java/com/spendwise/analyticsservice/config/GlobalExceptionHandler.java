package com.spendwise.analyticsservice.config;

import com.spendwise.common.exception.AbstractGlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Activates the shared RFC 7807 ProblemDetail handling from spendwise-common
 * for this service (Milestone 7) — only the {@code @RestControllerAdvice}
 * annotation and component-scan visibility live here; all mapping logic
 * (SpendWiseException -> ProblemDetail, plus the catch-all) is inherited from
 * {@link AbstractGlobalExceptionHandler}. Replaces the "no global exception
 * handler yet" deferral this service has carried since Milestone 1.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
}
