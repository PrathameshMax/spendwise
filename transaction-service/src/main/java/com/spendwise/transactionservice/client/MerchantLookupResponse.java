package com.spendwise.transactionservice.client;

import com.spendwise.transactionservice.api.MerchantDetails;

import java.util.List;

/**
 * Response body of MerchantDirectory's batch lookup. Merchants the partner
 * does not know are simply absent.
 */
public record MerchantLookupResponse(List<MerchantDetails> merchants) {
}
