package com.spendwise.transactionservice.service;

import com.spendwise.common.exception.ResourceNotFoundException;
import com.spendwise.transactionservice.api.CreateTransactionRequest;
import com.spendwise.transactionservice.api.TransactionFilter;
import com.spendwise.transactionservice.api.TransactionResponse;
import com.spendwise.transactionservice.client.AsyncUserServiceLookup;
import com.spendwise.transactionservice.client.ExchangeRateClient;
import com.spendwise.transactionservice.client.UserExistenceResponse;
import com.spendwise.transactionservice.domain.Category;
import com.spendwise.transactionservice.domain.CategoryRepository;
import com.spendwise.transactionservice.domain.Transaction;
import com.spendwise.transactionservice.domain.TransactionRepository;
import com.spendwise.transactionservice.domain.TransactionSpecifications;
import com.spendwise.transactionservice.event.TransactionCreatedEvent;
import com.spendwise.transactionservice.mapper.TransactionMapper;
import feign.FeignException;
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

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final TransactionMapper transactionMapper;
    private final AsyncUserServiceLookup asyncUserServiceLookup;
    private final ExchangeRateClient exchangeRateClient;

    public TransactionService(TransactionRepository transactionRepository,
                               CategoryRepository categoryRepository,
                               OutboxEventRecorder outboxEventRecorder,
                               TransactionMapper transactionMapper,
                               AsyncUserServiceLookup asyncUserServiceLookup,
                               ExchangeRateClient exchangeRateClient) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.outboxEventRecorder = outboxEventRecorder;
        this.transactionMapper = transactionMapper;
        this.asyncUserServiceLookup = asyncUserServiceLookup;
        this.exchangeRateClient = exchangeRateClient;
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
     * neither does (see {@code OutboxEvent}'s Javadoc for why a direct call
     * to the broker here would be the dual-write antipattern). The write
     * itself, and the correlation-id handling Milestone 17 added, moved to
     * {@link OutboxEventRecorder} at Milestone 21, which
     * {@code TransactionReversalService} shares.
     *
     * <p>Milestone 21 — the event now also carries the category name, which
     * budget-service matches budgets on.
     */
    private void recordOutboxEvent(Transaction saved) {
        outboxEventRecorder.record(saved.getId(), new TransactionCreatedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getCategory().getId(),
                saved.getCategory().getName(),
                saved.getAmount(),
                saved.getType(),
                saved.getTransactionDate(),
                saved.getCurrency(),
                saved.getBaseCurrencyAmount(),
                saved.getCreatedAt()));
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
