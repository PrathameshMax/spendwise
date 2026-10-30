package com.spendwise.transactionservice.service;

import com.spendwise.transactionservice.api.MerchantDetails;
import com.spendwise.transactionservice.api.TransactionResponse;
import com.spendwise.transactionservice.cache.MerchantDetailsCache;
import com.spendwise.transactionservice.client.MerchantDirectoryClient;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adds merchant display details (name, category, logo) to a statement page.
 *
 * <p>Order: kill switch, then the Redis cache (cache-aside), then MerchantDirectory
 * for cache misses only; fetched details are written back to the cache.
 *
 * <p>The merchant code comes from the payment narration: the second segment of UPI
 * and BBPS narrations ({@code UPI/BIGBASKET/411236721/GPay},
 * {@code BBPS/MSEDCL/ELECTRICITY BILL}). Other entries are returned unchanged.
 *
 * <p>Runs outside the database transaction (called from the controller after
 * {@code TransactionService.list} has returned).
 */
@Component
public class StatementMerchantEnricher {

    /** Kill switch, read on every request so a config refresh applies immediately. */
    static final String ENRICHMENT_ENABLED = "spendwise.merchant-directory.enrichment-enabled";

    private final MerchantDirectoryClient merchantDirectoryClient;
    private final MerchantDetailsCache merchantDetailsCache;
    private final Environment environment;

    public StatementMerchantEnricher(MerchantDirectoryClient merchantDirectoryClient,
                                     MerchantDetailsCache merchantDetailsCache,
                                     Environment environment) {
        this.merchantDirectoryClient = merchantDirectoryClient;
        this.merchantDetailsCache = merchantDetailsCache;
        this.environment = environment;
    }

    public Page<TransactionResponse> enrich(Page<TransactionResponse> page) {
        if (!environment.getProperty(ENRICHMENT_ENABLED, Boolean.class, Boolean.TRUE)) {
            return page;
        }

        Set<String> merchantCodes = page.getContent().stream()
                .map(entry -> merchantCode(entry.description()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (merchantCodes.isEmpty()) {
            return page;
        }

        Map<String, MerchantDetails> merchants = new HashMap<>(merchantDetailsCache.getAll(merchantCodes));
        Set<String> misses = merchantCodes.stream()
                .filter(code -> !merchants.containsKey(code))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!misses.isEmpty()) {
            Map<String, MerchantDetails> fetched = merchantDirectoryClient.lookup(misses);
            merchantDetailsCache.putAll(fetched);
            merchants.putAll(fetched);
        }
        if (merchants.isEmpty()) {
            return page;
        }

        return page.map(entry -> {
            String code = merchantCode(entry.description());
            MerchantDetails merchant = code == null ? null : merchants.get(code);
            return merchant == null ? entry : entry.withMerchant(merchant);
        });
    }

    static String merchantCode(String description) {
        if (description == null) {
            return null;
        }
        String[] segments = description.split("/");
        if (segments.length < 2 || !("UPI".equals(segments[0]) || "BBPS".equals(segments[0]))) {
            return null;
        }
        String code = segments[1].trim();
        return code.isEmpty() ? null : code;
    }
}