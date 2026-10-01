package com.spendwise.transactionservice.client;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Shape of the free, keyless exchange-rate API's {@code GET /v6/latest/{base}}
 * response (see {@code application.yml}'s {@code spendwise.exchange-rate
 * .base-url}): {@code {"result":"success","base_code":"USD","rates":{"INR":
 * 83.12,...}}}. Only the two fields this platform actually reads are
 * declared, same consumer-owned-contract convention as
 * {@link UserExistenceResponse}. This sandbox's own outbound network access
 * is restricted to package registries and GitHub, so this shape could not be
 * live-verified against the real API from here — it is built against that
 * provider's own published documentation. If the real response differs,
 * {@link ExchangeRateClient} is the one place to adjust; the Bulkhead/
 * fallback mechanics this milestone is actually about don't depend on getting
 * every field name exactly right on the first try.
 */
public record ExchangeRateApiResponse(String result, Map<String, BigDecimal> rates) {
}
