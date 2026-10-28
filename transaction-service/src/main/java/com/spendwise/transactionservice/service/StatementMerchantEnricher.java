package com.spendwise.transactionservice.service;

import com.spendwise.transactionservice.api.MerchantDetails;
import com.spendwise.transactionservice.api.TransactionResponse;
import com.spendwise.transactionservice.client.MerchantDirectoryClient;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adds merchant display details (name, category, logo) to a statement page.
 *
 * <p>The merchant code comes from the payment narration: the second segment
 * of UPI and BBPS narrations ({@code UPI/BIGBASKET/411236721/GPay},
 * {@code BBPS/MSEDCL/ELECTRICITY BILL}). Other entries (salary credits, ATM
 * withdrawals) have no merchant and are returned unchanged.
 *
 * <p>Runs outside the database transaction (called from the controller after
 * {@code TransactionService.list} has returned), so the partner call never
 * holds a database connection.
 */
@Component
public class StatementMerchantEnricher {

    private final MerchantDirectoryClient merchantDirectoryClient;

    public StatementMerchantEnricher(MerchantDirectoryClient merchantDirectoryClient) {
        this.merchantDirectoryClient = merchantDirectoryClient;
    }

    public Page<TransactionResponse> enrich(Page<TransactionResponse> page) {
        Set<String> merchantCodes = page.getContent().stream()
                .map(entry -> merchantCode(entry.description()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (merchantCodes.isEmpty()) {
            return page;
        }

        Map<String, MerchantDetails> merchants = merchantDirectoryClient.lookup(merchantCodes);
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
