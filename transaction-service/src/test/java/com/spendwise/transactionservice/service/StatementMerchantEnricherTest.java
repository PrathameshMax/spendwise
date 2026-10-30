package com.spendwise.transactionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.transactionservice.api.MerchantDetails;
import com.spendwise.transactionservice.api.TransactionResponse;
import com.spendwise.transactionservice.cache.MerchantDetailsCache;
import com.spendwise.transactionservice.client.MerchantDirectoryClient;
import com.spendwise.transactionservice.domain.TransactionStatus;
import com.spendwise.transactionservice.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatementMerchantEnricherTest {

    private static final MerchantDetails BIGBASKET =
            new MerchantDetails("BIGBASKET", "Bigbasket", "Groceries", "https://logos.example/bigbasket.png");
    private static final MerchantDetails MSEDCL =
            new MerchantDetails("MSEDCL", "Msedcl", "Bills & utilities", "https://logos.example/msedcl.png");

    @Test
    void extractsMerchantCodeFromUpiAndBbpsNarrations() {
        assertThat(StatementMerchantEnricher.merchantCode("UPI/BIGBASKET/411236721/GPay")).isEqualTo("BIGBASKET");
        assertThat(StatementMerchantEnricher.merchantCode("BBPS/MSEDCL/ELECTRICITY BILL")).isEqualTo("MSEDCL");
        assertThat(StatementMerchantEnricher.merchantCode("NEFT/SALARY OCT-2026/INFOSYS LTD")).isNull();
        assertThat(StatementMerchantEnricher.merchantCode("ATM WDL/HDFC ATM/Pune")).isNull();
        assertThat(StatementMerchantEnricher.merchantCode("Dinner")).isNull();
        assertThat(StatementMerchantEnricher.merchantCode(null)).isNull();
    }

    @Test
    void emptyCacheFetchesAllCodesInOneLookupAndCachesThem() {
        RecordingClient client = new RecordingClient(Map.of("BIGBASKET", BIGBASKET, "MSEDCL", MSEDCL));
        InMemoryCache cache = new InMemoryCache();
        Page<TransactionResponse> page = new PageImpl<>(List.of(
                entry("UPI/BIGBASKET/411236721/GPay"),
                entry("UPI/BIGBASKET/411236799/PhonePe"),
                entry("BBPS/MSEDCL/ELECTRICITY BILL"),
                entry("NEFT/SALARY OCT-2026/INFOSYS LTD")));

        Page<TransactionResponse> enriched = enricher(client, cache, true).enrich(page);

        assertThat(client.calls).hasSize(1);
        assertThat(client.calls.get(0)).containsExactly("BIGBASKET", "MSEDCL");
        assertThat(cache.store).containsKeys("BIGBASKET", "MSEDCL");
        assertThat(enriched.getContent()).extracting(TransactionResponse::merchant)
                .containsExactly(BIGBASKET, BIGBASKET, MSEDCL, null);
    }

    @Test
    void cachedMerchantsAreNotFetchedFromThePartner() {
        RecordingClient client = new RecordingClient(Map.of("BIGBASKET", BIGBASKET, "MSEDCL", MSEDCL));
        InMemoryCache cache = new InMemoryCache();
        cache.store.put("BIGBASKET", BIGBASKET);
        Page<TransactionResponse> page = new PageImpl<>(List.of(
                entry("UPI/BIGBASKET/411236721/GPay"),
                entry("BBPS/MSEDCL/ELECTRICITY BILL")));

        Page<TransactionResponse> enriched = enricher(client, cache, true).enrich(page);

        assertThat(client.calls).hasSize(1);
        assertThat(client.calls.get(0)).containsExactly("MSEDCL");
        assertThat(enriched.getContent()).extracting(TransactionResponse::merchant).containsExactly(BIGBASKET, MSEDCL);
    }

    @Test
    void fullyCachedPageMakesNoPartnerCall() {
        RecordingClient client = new RecordingClient(Map.of());
        InMemoryCache cache = new InMemoryCache();
        cache.store.put("BIGBASKET", BIGBASKET);
        Page<TransactionResponse> page = new PageImpl<>(List.of(entry("UPI/BIGBASKET/411236721/GPay")));

        Page<TransactionResponse> enriched = enricher(client, cache, true).enrich(page);

        assertThat(client.calls).isEmpty();
        assertThat(enriched.getContent()).extracting(TransactionResponse::merchant).containsExactly(BIGBASKET);
    }

    @Test
    void killSwitchOffSkipsCacheAndPartner() {
        RecordingClient client = new RecordingClient(Map.of("BIGBASKET", BIGBASKET));
        InMemoryCache cache = new InMemoryCache();
        cache.store.put("BIGBASKET", BIGBASKET);
        Page<TransactionResponse> page = new PageImpl<>(List.of(entry("UPI/BIGBASKET/411236721/GPay")));

        Page<TransactionResponse> enriched = enricher(client, cache, false).enrich(page);

        assertThat(client.calls).isEmpty();
        assertThat(cache.reads).isZero();
        assertThat(enriched.getContent()).extracting(TransactionResponse::merchant).containsExactly((MerchantDetails) null);
    }

    @Test
    void returnsStatementUnchangedWhenPartnerHasNoDetails() {
        RecordingClient client = new RecordingClient(Map.of());
        Page<TransactionResponse> page = new PageImpl<>(List.of(entry("UPI/SWIGGY/411236721/GPay")));

        Page<TransactionResponse> enriched = enricher(client, new InMemoryCache(), true).enrich(page);

        assertThat(enriched.getContent()).extracting(TransactionResponse::merchant).containsExactly((MerchantDetails) null);
    }

    @Test
    void skipsCacheAndPartnerWhenNoEntryHasAMerchant() {
        RecordingClient client = new RecordingClient(Map.of());
        InMemoryCache cache = new InMemoryCache();
        Page<TransactionResponse> page = new PageImpl<>(List.of(entry("ATM WDL/HDFC ATM/Pune")));

        enricher(client, cache, true).enrich(page);

        assertThat(client.calls).isEmpty();
        assertThat(cache.reads).isZero();
    }

    private static StatementMerchantEnricher enricher(RecordingClient client, InMemoryCache cache, boolean enabled) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(StatementMerchantEnricher.ENRICHMENT_ENABLED, String.valueOf(enabled));
        return new StatementMerchantEnricher(client, cache, environment);
    }

    private static TransactionResponse entry(String description) {
        return new TransactionResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Groceries",
                new BigDecimal("499.00"), TransactionType.EXPENSE, description, LocalDate.of(2026, 10, 12),
                Instant.parse("2026-10-12T09:15:00Z"), "INR", new BigDecimal("499.00"), TransactionStatus.POSTED,
                null, null, null);
    }

    private static final class RecordingClient extends MerchantDirectoryClient {

        private final Map<String, MerchantDetails> directory;
        private final List<List<String>> calls = new ArrayList<>();

        private RecordingClient(Map<String, MerchantDetails> directory) {
            super(RestClient.builder(), "http://merchant-directory.test", 500, 1500);
            this.directory = directory;
        }

        @Override
        public Map<String, MerchantDetails> lookup(Collection<String> names) {
            calls.add(List.copyOf(names));
            Map<String, MerchantDetails> found = new HashMap<>();
            for (String name : names) {
                MerchantDetails details = directory.get(name);
                if (details != null) {
                    found.put(name, details);
                }
            }
            return found;
        }
    }

    private static final class InMemoryCache extends MerchantDetailsCache {

        private final Map<String, MerchantDetails> store = new HashMap<>();
        private int reads;

        private InMemoryCache() {
            super(null, new ObjectMapper(), Duration.ofHours(24));
        }

        @Override
        public Map<String, MerchantDetails> getAll(Collection<String> merchantCodes) {
            reads++;
            Map<String, MerchantDetails> hits = new HashMap<>();
            for (String code : merchantCodes) {
                MerchantDetails details = store.get(code);
                if (details != null) {
                    hits.put(code, details);
                }
            }
            return hits;
        }

        @Override
        public void putAll(Map<String, MerchantDetails> merchants) {
            store.putAll(merchants);
        }
    }
}