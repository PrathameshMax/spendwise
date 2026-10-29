package com.spendwise.transactionservice.service;

import com.spendwise.transactionservice.api.MerchantDetails;
import com.spendwise.transactionservice.api.TransactionResponse;
import com.spendwise.transactionservice.client.MerchantDirectoryClient;
import com.spendwise.transactionservice.domain.TransactionStatus;
import com.spendwise.transactionservice.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
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
    void enrichesEntriesWithOneLookupPerPage() {
        RecordingClient client = new RecordingClient(Map.of("BIGBASKET", BIGBASKET, "MSEDCL", MSEDCL));
        Page<TransactionResponse> page = new PageImpl<>(List.of(
                entry("UPI/BIGBASKET/411236721/GPay"),
                entry("UPI/BIGBASKET/411236799/PhonePe"),
                entry("BBPS/MSEDCL/ELECTRICITY BILL"),
                entry("NEFT/SALARY OCT-2026/INFOSYS LTD")));

        Page<TransactionResponse> enriched = new StatementMerchantEnricher(client).enrich(page);

        assertThat(client.calls).hasSize(1);
        assertThat(client.calls.get(0)).containsExactly("BIGBASKET", "MSEDCL");
        assertThat(enriched.getContent()).extracting(TransactionResponse::merchant)
                .containsExactly(BIGBASKET, BIGBASKET, MSEDCL, null);
    }

    @Test
    void returnsStatementUnchangedWhenPartnerHasNoDetails() {
        RecordingClient client = new RecordingClient(Map.of());
        Page<TransactionResponse> page = new PageImpl<>(List.of(entry("UPI/SWIGGY/411236721/GPay")));

        Page<TransactionResponse> enriched = new StatementMerchantEnricher(client).enrich(page);

        assertThat(enriched.getContent()).extracting(TransactionResponse::merchant).containsExactly((MerchantDetails) null);
    }

    @Test
    void skipsPartnerCallWhenNoEntryHasAMerchant() {
        RecordingClient client = new RecordingClient(Map.of());
        Page<TransactionResponse> page = new PageImpl<>(List.of(entry("ATM WDL/HDFC ATM/Pune")));

        new StatementMerchantEnricher(client).enrich(page);

        assertThat(client.calls).isEmpty();
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
            super("http://merchant-directory.test");
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
}
