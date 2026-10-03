package com.spendwise.transactionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.exception.ResourceNotFoundException;
import com.spendwise.common.tracing.CorrelationIdConstants;
import com.spendwise.transactionservice.api.CreateTransactionRequest;
import com.spendwise.transactionservice.api.TransactionFilter;
import com.spendwise.transactionservice.api.TransactionResponse;
import com.spendwise.transactionservice.client.AsyncUserServiceLookup;
import com.spendwise.transactionservice.client.ExchangeRateClient;
import com.spendwise.transactionservice.client.UserExistenceResponse;
import com.spendwise.transactionservice.domain.Category;
import com.spendwise.transactionservice.domain.CategoryRepository;
import com.spendwise.transactionservice.domain.OutboxEvent;
import com.spendwise.transactionservice.domain.OutboxEventRepository;
import com.spendwise.transactionservice.domain.Transaction;
import com.spendwise.transactionservice.domain.TransactionRepository;
import com.spendwise.transactionservice.domain.TransactionSpecifications;
import com.spendwise.transactionservice.event.TransactionCreatedEvent;
import com.spendwise.transactionservice.mapper.TransactionMapper;
import feign.FeignException;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
public class TransactionService {

    private static final String AGGREGATE_TYPE_TRANSACTION = "Transaction";
    private static final int MAX_CORRELATION_ID_LENGTH = 64;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionMapper transactionMapper;
    private final AsyncUserServiceLookup asyncUserServiceLookup;
    private final ExchangeRateClient exchangeRateClient;
    private final ObjectMapper objectMapper;

    public TransactionService(TransactionRepository transactionRepository,
                               CategoryRepository categoryRepository,
                               OutboxEventRepository outboxEventRepository,
                               TransactionMapper transactionMapper,
                               AsyncUserServiceLookup asyncUserServiceLookup,
                               ExchangeRateClient exchangeRateClient,
                               ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.transactionMapper = transactionMapper;
        this.asyncUserServiceLookup = asyncUserServiceLookup;
        this.exchangeRateClient = exchangeRateClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        UserExistenceResponse user = verifyUserExists(request.userId());

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        BigDecimal baseCurrencyAmount = convertIfNeeded(
                request.amount(), request.currency(), user.preferredCurrency());

        Transaction saved = transactionRepository.save(new Transaction(
                request.userId(),
                category,
                request.amount(),
                request.type(),
                request.description(),
                request.transactionDate(),
                request.currency(),
                baseCurrencyAmount));

        recordOutboxEvent(saved);

        return transactionMapper.toResponse(saved);
    }

    /**
     * Milestone 16 — the second write of the Transactional Outbox pattern,
     * inside the same {@code @Transactional} boundary as
     * {@code transactionRepository.save(...)} above: both commit together or
     * neither does, which is the entire point (see {@link OutboxEvent}'s own
     * Javadoc for why a direct, separate call to a message broker here would
     * be the dual-write antipattern this milestone removes). No Kafka
     * producer is called — none exists on this platform yet (Milestone 17) —
     * this method's only job is to make the event durable and discoverable
     * for that future poller, not to deliver it anywhere.
     *
     * <p>{@code JsonProcessingException} is a checked exception on
     * {@code ObjectMapper#writeValueAsString}, but every field on
     * {@link TransactionCreatedEvent} is a Jackson-trivial type (UUID,
     * BigDecimal, an enum, a date/instant) with no custom serializer to fail
     * — treated here as the programming-error case it would actually be,
     * not a recoverable business outcome, hence the unchecked wrap rather
     * than adding a checked exception to this method's (and in turn
     * {@code TransactionController#create}'s) signature for a failure mode
     * that cannot occur for this payload shape.
     *
     * <p>Milestone 17 — the request's correlation id is read from the MDC
     * here, on the servlet request thread where {@code CorrelationIdFilter}
     * populated it, and persisted on the row, because the publisher that
     * later sends this event runs on a scheduler thread with no request
     * context. MDC is safe to read here (one thread per request, filter
     * clears it on exit) in a way it would not be on a reactive stack.
     */
    private void recordOutboxEvent(Transaction saved) {
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getCategory().getId(),
                saved.getAmount(),
                saved.getType(),
                saved.getTransactionDate(),
                saved.getCurrency(),
                saved.getBaseCurrencyAmount(),
                saved.getCreatedAt());

        try {
            outboxEventRepository.save(new OutboxEvent(
                    AGGREGATE_TYPE_TRANSACTION,
                    saved.getId(),
                    TransactionCreatedEvent.class.getSimpleName(),
                    objectMapper.writeValueAsString(event),
                    persistableCorrelationId()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Failed to serialize " + TransactionCreatedEvent.class.getSimpleName()
                            + " for transaction " + saved.getId(), ex);
        }
    }

    /**
     * The correlation id is client-supplied (the filters only replace a
     * missing or blank header), while {@code outbox_events.correlation_id} is
     * {@code VARCHAR(64)}. An over-long value is dropped rather than truncated
     * — a truncated id would no longer match anything — so a malformed header
     * can never fail the outbox insert and, with it, roll back the transaction.
     */
    private static String persistableCorrelationId() {
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);
        return correlationId != null && correlationId.length() <= MAX_CORRELATION_ID_LENGTH ? correlationId : null;
    }

    /**
     * Milestone 10, rewired in Milestone 13 — the platform's first real
     * synchronous inter-service call: before a transaction can be recorded
     * against a userId, that user must actually exist in user-service, and
     * (new in Milestone 13) its {@code preferredCurrency} is needed to decide
     * whether {@link #convertIfNeeded} has anything to do. Now routed through
     * {@link AsyncUserServiceLookup} (the {@code @TimeLimiter}-protected async
     * wrapper) instead of calling {@code UserServiceClient} directly — see
     * that class's Javadoc for why a separate wrapper bean was needed at all.
     *
     * <p>{@code CompletableFuture.join()} wraps any exceptional completion in
     * {@link CompletionException}; {@code AsyncUserServiceLookup}'s own
     * fallback already rethrows a real {@link FeignException.NotFound}
     * unchanged (not a wrapped/converted one) when that's genuinely what
     * happened, but {@code join()}'s own wrapping still happens on TOP of
     * that on the way out — so both the wrapped and (defensively) the
     * unwrapped shape are checked here, same reasoning as that class's own
     * fallback.
     */
    private UserExistenceResponse verifyUserExists(UUID userId) {
        try {
            return asyncUserServiceLookup.getByIdAsync(userId).join();
        } catch (CompletionException ex) {
            RuntimeException cause = unwrap(ex);
            if (cause instanceof FeignException.NotFound) {
                throw new ResourceNotFoundException("UserProfile", userId);
            }
            throw cause;
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("UserProfile", userId);
        }
    }

    /**
     * Milestone 13 — Functional Roadmap Workflow 1 Step 2: "if the entry is
     * in a foreign currency, converts it via a resilient WebClient call to
     * the external exchange-rate API." Skipped entirely (no network call at
     * all) when the entry already matches the user's own base currency — the
     * common case, and not something worth spending a bulkhead slot on.
     */
    private BigDecimal convertIfNeeded(BigDecimal amount, String entryCurrency, String preferredCurrency) {
        if (entryCurrency.equals(preferredCurrency)) {
            return null;
        }
        try {
            BigDecimal rate = exchangeRateClient.getConversionRate(entryCurrency, preferredCurrency).join();
            return amount.multiply(rate);
        } catch (CompletionException ex) {
            throw unwrap(ex);
        }
    }

    /**
     * {@code CompletableFuture.join()}'s own documented behavior: on
     * exceptional completion it always throws {@code CompletionException}
     * with the real cause attached, regardless of what that cause is —
     * unwrapping it here is what lets {@link
     * com.spendwise.common.exception.AbstractGlobalExceptionHandler}'s
     * {@code @ExceptionHandler(SpendWiseException.class)} actually catch a
     * {@code DownstreamServiceUnavailableException} produced by either
     * fallback below; left wrapped, it would fall through to the generic
     * catch-all and come back as an undifferentiated 500 instead of the
     * correct, type-safe 503.
     */
    private static RuntimeException unwrap(CompletionException ex) {
        if (ex.getCause() instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return ex;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(TransactionFilter filter, Pageable pageable) {
        Specification<Transaction> spec = TransactionSpecifications.combine(
                TransactionSpecifications.hasUserId(filter.userId()),
                TransactionSpecifications.hasCategoryId(filter.categoryId()),
                TransactionSpecifications.hasType(filter.type()),
                TransactionSpecifications.dateFrom(filter.fromDate()),
                TransactionSpecifications.dateTo(filter.toDate()),
                TransactionSpecifications.amountFrom(filter.minAmount()),
                TransactionSpecifications.amountTo(filter.maxAmount()));

        return transactionRepository.findAll(spec, pageable).map(transactionMapper::toResponse);
    }
}
