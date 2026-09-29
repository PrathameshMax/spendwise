package com.spendwise.common.exception;

import com.spendwise.common.tracing.CorrelationIdConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Shared RFC 7807 ProblemDetail mapping for every servlet-based service
 * (Milestone 7) — replaces the ad-hoc "let it surface as a generic 500"
 * behavior every service has carried since Milestone 1 (see the README's
 * Milestone 1-5 status notes).
 *
 * Each service activates this via its own thin {@code @RestControllerAdvice}
 * subclass (see e.g. auth-service's {@code config.GlobalExceptionHandler})
 * rather than this class being annotated/scanned directly: Spring Boot's
 * default component scan is limited to each service's own base package, and
 * putting {@code @RestControllerAdvice} here would silently do nothing.
 *
 * Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own built-in
 * exceptions (malformed request bodies, unsupported media types, missing
 * request parameters, and — as of Milestone 8's {@code @Valid}/{@code @Validated}
 * annotations — method-argument validation failures) already come back as RFC
 * 7807 ProblemDetail via that base class's own handling; this class adds mapping
 * for the {@link SpendWiseException} hierarchy and a final catch-all so
 * nothing ever reaches a client as a bare, undocumented 500.
 *
 * {@link #handleMethodArgumentNotValid} narrows that inherited handling
 * (Milestone 8): the base class already turns a failed {@code @Valid}/
 * {@code @Validated} request body into a ProblemDetail with a 400 status and a
 * per-field {@code errors} array, but it has no way to know about this
 * platform's own {@code errorCode}/{@code correlationId} response contract.
 * Without this override, a validation failure would be the one error path on
 * the whole platform whose response shape doesn't match every other error —
 * this closes that gap by enriching the same ProblemDetail the base class
 * already built rather than replacing it.
 */
public abstract class AbstractGlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractGlobalExceptionHandler.class);

    private static final String ERROR_CODE_PROPERTY = "errorCode";
    private static final String CORRELATION_ID_PROPERTY = "correlationId";
    private static final String GENERIC_ERROR_CODE = "INTERNAL_SERVER_ERROR";
    private static final String VALIDATION_ERROR_CODE = "VALIDATION_FAILED";

    @ExceptionHandler(SpendWiseException.class)
    public ResponseEntity<ProblemDetail> handleSpendWiseException(SpendWiseException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
        enrich(problem, ex.getErrorCode());
        return ResponseEntity.status(ex.getHttpStatus()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception reached the global handler", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        enrich(problem, GENERIC_ERROR_CODE);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleMethodArgumentNotValid(ex, headers, status, request);
        if (response.getBody() instanceof ProblemDetail problem) {
            enrich(problem, VALIDATION_ERROR_CODE);
        }
        return response;
    }

    private void enrich(ProblemDetail problem, String errorCode) {
        problem.setProperty(ERROR_CODE_PROPERTY, errorCode);
        problem.setProperty(CORRELATION_ID_PROPERTY, MDC.get(CorrelationIdConstants.MDC_KEY));
    }
}
