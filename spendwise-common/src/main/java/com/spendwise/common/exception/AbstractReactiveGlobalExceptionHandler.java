package com.spendwise.common.exception;

import com.spendwise.common.tracing.CorrelationIdConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.List;

/**
 * Milestone 15 — the WebFlux-stack twin of {@link AbstractGlobalExceptionHandler},
 * introduced because analytics-service's reactive rebuild (Spring Data R2DBC +
 * a WebFlux controller calling budget-service over gRPC) cannot reuse that class
 * as-is: {@link AbstractGlobalExceptionHandler} extends Spring MVC's own
 * {@code org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler},
 * which requires {@code spring-webmvc}/the Servlet API on the classpath — exactly
 * the dependency this platform's reactive services must never carry (see this
 * module's pom.xml, where both base classes' frameworks are "provided" so neither
 * leaks into the other stack's services). This class extends the WebFlux sibling,
 * {@code org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler},
 * and otherwise produces byte-for-byte the same RFC 7807 ProblemDetail contract
 * (errorCode/correlationId enrichment, the per-field {@code errors} array on a
 * validation failure) every servlet-based service already returns.
 *
 * <p>Each reactive service activates this via its own thin
 * {@code @RestControllerAdvice} subclass, the same "component-scan can't see a
 * class outside the service's own base package" reasoning
 * {@link AbstractGlobalExceptionHandler}'s own Javadoc already documents — see
 * analytics-service's {@code config.GlobalExceptionHandler}.
 *
 * <p>Two real shape differences from the servlet twin. First, WebFlux has no
 * {@code MethodArgumentNotValidException} (that type is tied to Spring MVC's own
 * method-argument resolution during servlet dispatch); a failed
 * {@code @Valid @RequestBody} binding in a WebFlux handler instead throws
 * {@link WebExchangeBindException}, which — unlike its MVC counterpart — directly
 * implements {@code BindingResult} rather than merely exposing one via a getter,
 * and whose override point on the base class is {@code handleWebExchangeBindException}
 * returning {@code Mono<ResponseEntity<Object>>} rather than a bare
 * {@code ResponseEntity<Object>} (confirmed directly against spring-webflux's own
 * {@code ResponseEntityExceptionHandler} source at tag v6.2.1, the exact
 * dependency-managed Spring Framework version this platform's spring-boot.version=
 * 3.4.1 BOM pulls in). Second, the correlation id this class enriches every
 * ProblemDetail with cannot be read via {@code MDC.get(...)} the way the servlet
 * twin reads it: {@code ReactiveCorrelationIdFilter} deliberately never puts it
 * into the thread-local MDC at all (seeing its Javadoc for why that would be
 * unsafe under WebFlux's multiplexed-thread execution model), only into Reactor
 * {@code Context} — so every handler method here is wrapped in
 * {@code Mono.deferContextual(...)} to read it back out of that same Context
 * instead.
 */
public abstract class AbstractReactiveGlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractReactiveGlobalExceptionHandler.class);

    private static final String ERROR_CODE_PROPERTY = "errorCode";
    private static final String CORRELATION_ID_PROPERTY = "correlationId";
    private static final String FIELD_ERRORS_PROPERTY = "errors";
    private static final String GENERIC_ERROR_CODE = "INTERNAL_SERVER_ERROR";
    private static final String VALIDATION_ERROR_CODE = "VALIDATION_FAILED";

    @ExceptionHandler(SpendWiseException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleSpendWiseException(SpendWiseException ex) {
        return Mono.deferContextual(ctx -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
            enrich(problem, ex.getErrorCode(), ctx);
            return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(problem));
        });
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ProblemDetail>> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception reached the global handler", ex);
        return Mono.deferContextual(ctx -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
            enrich(problem, GENERIC_ERROR_CODE, ctx);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem));
        });
    }

    @Override
    protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(
            WebExchangeBindException ex, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
        return Mono.deferContextual(ctx ->
                super.handleWebExchangeBindException(ex, headers, status, exchange)
                        .doOnNext(response -> {
                            if (response.getBody() instanceof ProblemDetail problem) {
                                enrich(problem, VALIDATION_ERROR_CODE, ctx);
                                List<FieldValidationError> errors = ex.getFieldErrors().stream()
                                        .map(fieldError -> new FieldValidationError(fieldError.getField(), fieldError.getDefaultMessage()))
                                        .toList();
                                problem.setProperty(FIELD_ERRORS_PROPERTY, errors);
                            }
                        }));
    }

    private void enrich(ProblemDetail problem, String errorCode, ContextView ctx) {
        problem.setProperty(ERROR_CODE_PROPERTY, errorCode);
        problem.setProperty(CORRELATION_ID_PROPERTY, ctx.getOrDefault(CorrelationIdConstants.MDC_KEY, null));
    }
}
