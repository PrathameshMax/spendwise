package com.spendwise.transactionservice.client;

import java.util.List;

/**
 * Request body of MerchantDirectory's batch lookup: one call per statement
 * page, carrying every distinct merchant code on that page.
 */
public record MerchantLookupRequest(List<String> names) {
}
