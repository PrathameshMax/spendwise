package com.spendwise.transactionservice.api;

/**
 * Display details for the merchant behind a statement entry, as returned by
 * the MerchantDirectory partner API: a readable name, a spending category and
 * a logo for the mobile statement screen.
 *
 * @param name        merchant code as it appears in the payment narration (e.g. BIGBASKET)
 * @param displayName name to show to the customer
 * @param category    partner's merchant category
 * @param logoUrl     logo image URL
 */
public record MerchantDetails(String name, String displayName, String category, String logoUrl) {
}
