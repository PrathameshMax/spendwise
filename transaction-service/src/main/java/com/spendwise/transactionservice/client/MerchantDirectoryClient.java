package com.spendwise.transactionservice.client;

import com.spendwise.transactionservice.api.MerchantDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Client for the MerchantDirectory partner API (merchant display name,
 * category and logo), used to enrich statement entries.
 *
 * <p>Merchant details are cosmetic: when the partner is unavailable the
 * statement is still returned, just without logos. Any failure is therefore
 * logged and turned into an empty result instead of failing the request.
 */
@Component
public class MerchantDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(MerchantDirectoryClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public MerchantDirectoryClient(
            @Value("${spendwise.merchant-directory.base-url:http://merchant-directory:8099}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Looks up all given merchant codes in one call.
     *
     * @return details keyed by merchant code; empty if the partner fails
     */
    public Map<String, MerchantDetails> lookup(Collection<String> names) {
        if (names.isEmpty()) {
            return Map.of();
        }
        try {
            MerchantLookupResponse response = restTemplate.postForObject(
                    baseUrl + "/v1/merchants/lookup",
                    new MerchantLookupRequest(List.copyOf(names)),
                    MerchantLookupResponse.class);
            if (response == null || response.merchants() == null) {
                return Map.of();
            }
            return response.merchants().stream()
                    .collect(Collectors.toMap(MerchantDetails::name, Function.identity(), (first, second) -> first));
        } catch (RestClientException ex) {
            log.warn("MerchantDirectory lookup failed for {} merchants, returning statement without merchant details: {}",
                    names.size(), ex.toString());
            return Map.of();
        }
    }
}
