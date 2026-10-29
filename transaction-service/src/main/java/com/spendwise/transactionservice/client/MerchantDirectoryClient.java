package com.spendwise.transactionservice.client;

import com.spendwise.transactionservice.api.MerchantDetails;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
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

    private final RestClient restClient;

    public MerchantDirectoryClient(RestClient.Builder restClientBuilder,
                                     @Value("${spendwise.merchant-directory.base-url:http://merchant-directory:8099}") String baseUrl,
                                     @Value("${spendwise.merchant-directory.connect-timeout-ms:500}") long connectTimeoutMs,
                                     @Value("${spendwise.merchant-directory.connect-timeout-ms:1500}") long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Looks up all given merchant codes in one call.
     *
     * @return details keyed by merchant code; empty when the partner fails, is slow,
     *         or the circuit is open
     */
    @CircuitBreaker(name = "merchantDirectory", fallbackMethod = "lookupFallback")
    @Bulkhead(name = "merchantDirectory")
    public Map<String, MerchantDetails> lookup(Collection<String> names) {
        if (names.isEmpty()) {
            return Map.of();
        }
            MerchantLookupResponse response = restClient.post()
                    .uri("/v1/merchants/lookup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MerchantLookupRequest(List.copyOf(names)))
                    .retrieve()
                    .body(MerchantLookupResponse.class);

            if (response == null || response.merchants() == null) {
                return Map.of();
            }
            return response.merchants().stream()
                    .collect(Collectors.toMap(MerchantDetails::name, Function.identity(), (first, second) -> first));
    }



    /**
     * Fallback for any failure of {@link #lookup}: timeouts, cancellations, partner
     * errors, an open circuit or a full bulkhead. An open circuit and a full bulkhead
     * are expected protective states and are not logged per request; anything else is
     * logged with its type so an unexpected failure (or a bug) stays visible.
     */
    public Map<String, MerchantDetails> lookupFallback(Collection<String> names, Throwable throwable){

        if (!(throwable instanceof CallNotPermittedException) || !(throwable instanceof BulkheadFullException)){
            log.warn("MerchantDirectory lookup failed for {} merchants ({}), returning statement without merchant details",
                    names.size(), throwable.toString());
        }
        return Map.of();

    }

}
